package dev.couchtv.launcher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.couchtv.launcher.apk.ApkInspector
import dev.couchtv.launcher.apk.ApkInstaller
import dev.couchtv.launcher.data.AppCatalog
import dev.couchtv.launcher.data.AppCompatDatabase
import dev.couchtv.launcher.data.AppEntry
import dev.couchtv.launcher.data.LauncherSettings
import dev.couchtv.launcher.data.LauncherStore
import dev.couchtv.launcher.data.ThemeMode
import dev.couchtv.launcher.data.TvCompat
import dev.couchtv.launcher.notifications.NotificationRelay
import dev.couchtv.launcher.remote.InputRouter
import dev.couchtv.launcher.remote.Protocol
import dev.couchtv.launcher.remote.RemoteApp
import dev.couchtv.launcher.remote.RemoteHub
import dev.couchtv.launcher.remote.RemoteInputSink
import dev.couchtv.launcher.remote.RemoteService
import dev.couchtv.launcher.remote.RemoteState
import dev.couchtv.launcher.remote.UploadBus
import dev.couchtv.launcher.streaming.MediaItem
import dev.couchtv.launcher.streaming.StreamingHub
import dev.couchtv.launcher.streaming.StreamingServiceAdapter
import dev.couchtv.launcher.util.NetworkInfo
import dev.couchtv.launcher.util.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import dev.couchtv.launcher.R
import androidx.core.graphics.drawable.toBitmap

/**
 * The launcher's brain.
 *
 * One `StateFlow<UiState>` drives every screen, one focus position drives the D-pad, and every input
 * path — the device's remote, a controller, touch, or the phone remote — funnels into the same
 * functions. That is what keeps "the remote works everywhere" true: there is no second code path for
 * pointer or network input.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application), RemoteInputSink {

    data class UiState(
        val loading: Boolean = true,
        val onboarding: Boolean = false,
        val apps: List<AppEntry> = emptyList(),
        val selected: List<String> = emptyList(),
        val settings: LauncherSettings = LauncherSettings(),
        val pins: List<String> = emptyList(),
        val streaming: StreamingHub.Snapshot = StreamingHub.Snapshot(),
        val rows: List<HomeRow> = emptyList(),
        val focus: FocusPos = FocusPos(0, 0),
        val screen: Screen = Screen.HOME,
        val overlay: Overlay? = null,
        val toast: String? = null,
        val newApps: List<AppEntry> = emptyList(),
        val addPrompt: AddPrompt? = null,
        val compatPrompt: CompatPrompt? = null,
        val parentalRequest: ParentalRequest? = null,
        val serviceMissing: String? = null,
        val installBusy: Boolean = false,
        val permissionNeeded: Boolean = false,
        val query: String = "",
        val pointerMode: Boolean = false,
        val cursorVisible: Boolean = false,
        val cursorX: Float = 0f,
        val cursorY: Float = 0f,
        val saverActive: Boolean = false,
        val network: NetworkStatus = NetworkStatus(null, true, -1, null, false),
        val remoteClients: Int = 0,
        val pairingCode: String = "------",
        val remoteRunning: Boolean = false,
        val uploadUrl: String? = null,
        val installStatus: String? = null,
    ) {
        fun rowSizes(): List<Int> = rows.map { it.items.size }

        val selectedApps: List<AppEntry>
            get() = selected.mapNotNull { id -> apps.firstOrNull { it.packageName == id } }

        val searchApps: List<AppEntry>
            get() = if (query.isBlank()) emptyList() else apps.filter {
                it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }

        val searchMedia: List<MediaItem>
            get() {
                if (query.isBlank()) return emptyList()
                val all = streaming.rows.continueWatching + streaming.rows.trending +
                    streaming.rows.movies + streaming.rows.series
                return all.filter { it.title.contains(query, ignoreCase = true) }.distinctBy { it.id }
            }
    }

    private val context: Context = application.applicationContext
    private val store = LauncherStore(context)
    private val compatDb = AppCompatDatabase(context)
    private val catalog = AppCatalog(context, compatDb)
    private val inspector = ApkInspector(context)
    private val installer = ApkInstaller(context)
    private val streaming = StreamingHub(context, store)

    /** The activity sets a key dispatcher here so remote keys reach the Compose tree. */
    val inputRouter = InputRouter(context)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Set by the composition while pointer mode is on; null the rest of the time. */
    @Volatile
    var cursorRegistry: CursorRegistry? = null
        private set

    private var hovered: CursorRegistry.Target? = null
    private var lastInputAt = System.currentTimeMillis()
    private var idleWatcher: Job? = null
    private var installWatcher: BroadcastReceiver? = null
    private var screenWidth = 1920f
    private var screenHeight = 1080f
    private var remoteStarted = false

    init {
        observe()
        bootstrap()
        RemoteHub.sink = this
        RemoteHub.stateProvider = { remoteState() }
        RemoteHub.appsProvider = { remoteApps() }
        startIdleWatcher()
        watchInstalls()
    }

    // ---- Boot ------------------------------------------------------------------------------------

    private fun observe() {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                rebuildRows()
                syncRemote(settings)
            }
        }
        viewModelScope.launch {
            store.selectedApps.collect { selected ->
                _state.update { it.copy(selected = selected) }
                rebuildRows()
            }
        }
        viewModelScope.launch {
            store.pins.collect { pins ->
                _state.update { it.copy(pins = pins) }
                rebuildRows()
            }
        }
        viewModelScope.launch {
            NotificationRelay.items.collect {
                rebuildRows()
            }
        }
        viewModelScope.launch {
            streaming.snapshot.collect { snapshot ->
                _state.update { it.copy(streaming = snapshot) }
                rebuildRows()
            }
        }
        viewModelScope.launch {
            RemoteHub.clientCount.collect { count ->
                _state.update { it.copy(remoteClients = count) }
            }
        }
        viewModelScope.launch {
            RemoteHub.pairingCode.collect { code -> _state.update { it.copy(pairingCode = code) } }
        }
        // A file dropped from a laptop lands here.
        viewModelScope.launch {
            UploadBus.pending.filterNotNull().collect {
                UploadBus.consume()?.let(::installFile)
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val settings = store.snapshot()
            refreshApps(force = true)
            refreshNetwork()
            streaming.loadCached()
            _state.update {
                it.copy(
                    loading = false,
                    onboarding = !settings.onboarded,
                    uploadUrl = if (settings.remoteEnabled) RemoteHub.httpUrl() else null,
                )
            }
            // Recommendations arrive behind the glass; first frame never waits for them.
            launch {
                streaming.refresh()
            }
        }
    }

    fun refreshNetwork() {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { NetworkInfo.status(context) }
            _state.update { it.copy(network = status, uploadUrl = RemoteHub.httpUrl()) }
        }
    }

    fun refreshApps(force: Boolean = false) {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { catalog.installed(force) }
            val seen = runCatching { store.seenPackages.first() }.getOrDefault(emptySet())
            val selected = _state.value.selected
            val fresh = apps.filter { it.packageName !in selected && it.packageName !in seen }
            _state.update { it.copy(apps = apps, newApps = fresh.take(3)) }
            rebuildRows()
        }
    }

    // ---- Rows ------------------------------------------------------------------------------------

    /**
     * Builds the home screen, in the order the brief fixes: Continue Watching, Trending, Your apps,
     * then recommendations — with Favorites and Notifications ahead of the rest when they exist.
     * Only apps the user chose appear.
     */
    private fun rebuildRows() {
        val current = _state.value
        val palette = current.selectedApps
        val rows = buildList {
            add(
                HomeRow(
                    id = "status",
                    title = null,
                    kind = RowKind.STATUS,
                    items = buildList {
                        // A newly installed app is offered here, where the D-pad already lives.
                        current.newApps.firstOrNull()?.let { entry ->
                            add(HomeItem.Action(ACTION_ADD_NEW, context.getString(R.string.add_app_named, entry.label)))
                            add(HomeItem.Action(ACTION_DISMISS_NEW, context.getString(R.string.dismiss)))
                        }
                        add(HomeItem.Action(ACTION_SEARCH, context.getString(R.string.search)))
                        add(HomeItem.Action(ACTION_QUICK, context.getString(R.string.quick_settings)))
                        add(HomeItem.Action(ACTION_SETTINGS, context.getString(R.string.settings)))
                    },
                )
            )

            val favorites = buildList {
                palette.filter { it.packageName in current.pins }.forEach { add(HomeItem.App(it)) }
                streamingSnapshotPinned(current).forEach { add(HomeItem.Media(it)) }
            }
            if (favorites.isNotEmpty()) {
                add(HomeRow("favorites", context.getString(R.string.row_favorites), RowKind.FAVORITES, favorites))
            }

            if (current.streaming.rows.continueWatching.isNotEmpty()) {
                add(
                    HomeRow(
                        "continue",
                        context.getString(R.string.row_continue),
                        RowKind.CONTINUE,
                        current.streaming.rows.continueWatching.map { HomeItem.Media(it) },
                    )
                )
            }

            if (current.streaming.rows.trending.isNotEmpty()) {
                add(
                    HomeRow(
                        "trending",
                        context.getString(R.string.row_trending),
                        RowKind.TRENDING,
                        current.streaming.rows.trending.map { HomeItem.Media(it) },
                    )
                )
            }

            add(
                HomeRow(
                    "apps",
                    context.getString(R.string.row_your_apps),
                    RowKind.APPS,
                    palette.map { HomeItem.App(it) } + HomeItem.Action(ACTION_ADD_APPS, context.getString(R.string.add_apps)),
                )
            )

            if (current.streaming.rows.movies.isNotEmpty()) {
                add(
                    HomeRow(
                        "movies",
                        context.getString(R.string.row_movies),
                        RowKind.MOVIES,
                        current.streaming.rows.movies.map { HomeItem.Media(it) },
                    )
                )
            }

            if (current.streaming.rows.series.isNotEmpty()) {
                add(
                    HomeRow(
                        "series",
                        context.getString(R.string.row_series),
                        RowKind.SERIES,
                        current.streaming.rows.series.map { HomeItem.Media(it) },
                    )
                )
            }

            val notices = NotificationRelay.items.value
            if (current.settings.notificationsRow && notices.isNotEmpty()) {
                add(
                    HomeRow(
                        "notifications",
                        context.getString(R.string.row_notifications),
                        RowKind.NOTIFICATIONS,
                        notices.take(6).map { HomeItem.Notice(it) },
                    )
                )
            }
        }

        val sizes = rows.map { it.items.size }
        val focus = FocusEngine.clamp(current.focus, sizes)
        _state.update { it.copy(rows = rows, focus = focus) }
    }

    private fun streamingSnapshotPinned(state: UiState): List<MediaItem> {
        if (state.pins.isEmpty()) return emptyList()
        val all = state.streaming.rows.continueWatching + state.streaming.rows.trending +
            state.streaming.rows.movies + state.streaming.rows.series
        return all.filter { it.id in state.pins }.distinctBy { it.id }.take(8)
    }

    // ---- Focus -----------------------------------------------------------------------------------

    fun moveFocus(direction: Direction) {
        noteActivity()
        val current = _state.value
        val next = FocusEngine.move(current.focus, direction, current.rowSizes())
        if (next == current.focus) return
        _state.update { it.copy(focus = next) }
        announceFocus(next)
    }

    /** Brings a row into view by touching it (touch devices, pointer). */
    fun focusOn(row: Int, col: Int) {
        val current = _state.value
        val next = FocusEngine.clamp(FocusPos(row, col), current.rowSizes())
        if (next == current.focus) return
        _state.update { it.copy(focus = next) }
        announceFocus(next)
    }

    /** Lets a connected phone follow the focus, and nothing else. */
    private fun announceFocus(pos: FocusPos) {
        if (!remoteStarted) return
        RemoteHub.broadcastState()
    }

    fun activateFocus() {
        noteActivity()
        val current = _state.value
        val item = current.rows.getOrNull(current.focus.row)?.items?.getOrNull(current.focus.col)
        if (item != null) activate(item)
    }

    private fun activate(item: HomeItem) = when (item) {
        is HomeItem.App -> launchApp(item.entry)
        is HomeItem.Media -> openMedia(item.item)
        is HomeItem.Action -> runAction(item.action)
        is HomeItem.Notice -> NotificationRelay.dismiss(item.item.key)
    }

    private fun runAction(action: String) = when (action) {
        ACTION_SEARCH -> openSearch()
        ACTION_QUICK -> toggleQuickSettings()
        ACTION_SETTINGS -> openSettings()
        ACTION_ADD_APPS -> openScreen(Screen.MANAGE_APPS)
        ACTION_ADD_NEW -> _state.value.newApps.firstOrNull()?.let { addNewApp(it) }
        ACTION_DISMISS_NEW -> _state.value.newApps.firstOrNull()?.let { dismissNewApp(it) }
        else -> Unit
    }

    // ---- Launching apps --------------------------------------------------------------------------

    fun launchApp(entry: AppEntry) {
        noteActivity()
        if (isLocked(entry.packageName) && _state.value.settings.parentalEnabled) {
            _state.update { it.copy(parentalRequest = ParentalRequest(entry)) }
            return
        }
        val intent = catalog.launchIntent(entry)
        if (intent == null) {
            _state.update { it.copy(toast = "${entry.label} can't be opened.") }
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _state.update { state -> state.copy(toast = context.getString(R.string.error_open_media_named, entry.label)) } }
    }

    fun submitPin(pin: String) {
        val request = _state.value.parentalRequest ?: return
        viewModelScope.launch {
            val digest = store.parentalPinDigest()
            if (digest != null && dev.couchtv.launcher.remote.Pairing.digest(pin) == digest) {
                _state.update { it.copy(parentalRequest = null) }
                val entry = request.app
                val intent = catalog.launchIntent(entry)
                runCatching { intent?.let { context.startActivity(it) } }
            } else {
                _state.update { it.copy(toast = context.getString(R.string.pin_wrong)) }
            }
        }
    }

    fun cancelPin() = _state.update { it.copy(parentalRequest = null) }

    fun openMedia(item: MediaItem) {
        noteActivity()
        val intent = streaming.deepLink(item)
        if (intent == null) {
            _state.update { it.copy(serviceMissing = item.serviceId) }
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _state.update { it.copy(toast = context.getString(R.string.error_open_media)) } }
    }

    fun dismissServiceMissing() = _state.update { it.copy(serviceMissing = null) }

    fun installServiceApp(serviceId: String) {
        val packageName = streaming.packageName(serviceId) ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { openSideload() }
        _state.update { it.copy(serviceMissing = null) }
    }

    // ---- Screens ---------------------------------------------------------------------------------

    fun openScreen(screen: Screen) {
        noteActivity()
        _state.update { it.copy(screen = screen, overlay = null, query = if (screen == Screen.SEARCH) it.query else "") }
        if (screen == Screen.SEARCH) {
            // Start the caret at the search field; the field itself takes real focus.
            _state.update { it.copy(focus = FocusPos(0, 0)) }
        }
    }

    fun openSettings() = openScreen(Screen.SETTINGS)

    fun openSideload() = openScreen(Screen.SIDELOAD)

    fun openPairing() = openScreen(Screen.PAIRING)

    fun openSearch(prefill: String? = null) {
        _state.update {
            it.copy(
                screen = Screen.SEARCH,
                overlay = null,
                query = prefill ?: it.query,
            )
        }
    }

    fun setQuery(text: String) = _state.update { it.copy(query = text.take(64)) }

    /** Back behaves like tvOS: leave the screen, else close the overlay, else nothing. */
    fun back(): Boolean {
        noteActivity()
        val current = _state.value
        return when {
            current.parentalRequest != null -> { cancelPin(); true }
            current.addPrompt != null -> { dismissAddPrompt(); true }
            current.compatPrompt != null -> { dismissCompatPrompt(); true }
            current.overlay != null -> { _state.update { it.copy(overlay = null, saverActive = false) }; true }
            current.screen != Screen.HOME -> { _state.update { it.copy(screen = Screen.HOME) }; true }
            else -> false
        }
    }

    fun goHome() {
        _state.update { it.copy(screen = Screen.HOME, overlay = null, saverActive = false) }
        _state.update { it.copy(focus = FocusPos(0, 0)) }
    }

    fun toggleQuickSettings() {
        noteActivity()
        _state.update {
            if (it.overlay == Overlay.QUICK_SETTINGS) it.copy(overlay = null)
            else it.copy(overlay = Overlay.QUICK_SETTINGS)
        }
    }

    fun startSaver() {
        _state.update { it.copy(saverActive = true, overlay = null) }
    }

    fun stopSaver() {
        noteActivity()
        if (_state.value.saverActive) _state.update { it.copy(saverActive = false) }
    }

    fun showToast(message: String) = _state.update { it.copy(toast = message) }

    fun clearToast() = _state.update { it.copy(toast = null) }

    // ---- Settings --------------------------------------------------------------------------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }

    fun setSaverDelay(minutes: Int) = viewModelScope.launch { store.setSaverDelay(minutes) }

    fun setSaverClock(on: Boolean) = viewModelScope.launch { store.setSaverClock(on) }

    fun setRemoteEnabled(enabled: Boolean) = viewModelScope.launch {
        store.setRemoteEnabled(enabled)
        if (enabled) startRemote(_state.value.settings.remotePort) else stopRemote()
    }

    fun setAutostart(enabled: Boolean) = viewModelScope.launch { store.setAutostart(enabled) }

    fun setNotificationsRow(enabled: Boolean) = viewModelScope.launch { store.setNotificationsRow(enabled) }

    fun setParentalEnabled(enabled: Boolean) = viewModelScope.launch { store.setParentalEnabled(enabled) }

    fun setParentalPin(pin: String) = viewModelScope.launch {
        if (pin.length in 4..8 && pin.all { it.isDigit() }) {
            store.setParentalPin(dev.couchtv.launcher.remote.Pairing.digest(pin))
            showToast(context.getString(R.string.parental_pin_saved))
        } else {
            showToast(context.getString(R.string.parental_pin_invalid))
        }
    }

    fun toggleLocked(packageName: String) = viewModelScope.launch { store.toggleLocked(packageName) }

    fun isLocked(packageName: String): Boolean = packageName in _state.value.settings.lockedPackages

    fun newPairingCode() = viewModelScope.launch {
        RemoteHub.newPairingCode()
        showToast(context.getString(R.string.pairing_code_new))
    }

    fun revokeDevices() {
        RemoteHub.pairingStore()?.revokeAll()
        showToast(context.getString(R.string.pairing_revoked))
    }

    fun togglePointerMode(on: Boolean = !_state.value.pointerMode) {
        _state.update {
            it.copy(pointerMode = on, cursorVisible = on, cursorX = screenWidth / 2f, cursorY = screenHeight / 2f)
        }
        if (!on) {
            cursorRegistry?.clear()
            hovered = null
        }
    }

    fun attachCursorRegistry(registry: CursorRegistry?) {
        cursorRegistry = registry
    }

    // ---- Onboarding / app choices ----------------------------------------------------------------

    fun toggleSelected(packageName: String) = viewModelScope.launch {
        store.toggleSelected(packageName)
    }

    fun selectAll() = viewModelScope.launch {
        store.setSelected(_state.value.apps.map { it.packageName })
    }

    fun deselectAll() = viewModelScope.launch { store.setSelected(emptyList()) }

    fun finishOnboarding() = viewModelScope.launch {
        // Everything installed now is "known"; anything installed later is offered.
        _state.value.apps.forEach { store.markSeen(it.packageName) }
        store.setOnboarded(true)
        _state.update { it.copy(onboarding = false) }
        _state.update { it.copy(focus = FocusPos(0, 0)) }
        refreshApps(force = true)
    }

    fun togglePin(id: String) = viewModelScope.launch { store.togglePin(id) }

    fun dismissNewApp(entry: AppEntry) = viewModelScope.launch {
        store.markSeen(entry.packageName)
        _state.update { it.copy(newApps = it.newApps.filterNot { app -> app.packageName == entry.packageName }) }
    }

    fun addNewApp(entry: AppEntry) = viewModelScope.launch {
        store.markSeen(entry.packageName)
        store.toggleSelected(entry.packageName)
        _state.update { it.copy(newApps = it.newApps.filterNot { app -> app.packageName == entry.packageName }) }
        showToast("${entry.label} added")
    }

    fun markTouchOnly(entry: AppEntry) {
        compatDb.setUserVerdict(entry.packageName, versionCodeOf(entry), TvCompat.TOUCH_ONLY)
        refreshApps(force = true)
        showToast(context.getString(R.string.compat_marked))
    }

    private fun versionCodeOf(entry: AppEntry): Long = runCatching {
        val info = context.packageManager.getPackageInfo(entry.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    // ---- APK install -----------------------------------------------------------------------------

    /** The file picker's result (or a Share intent) arrives here. */
    fun installFromUri(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (file == null) {
                _state.update { it.copy(installStatus = context.getString(R.string.error_read_file)) }
                return@launch
            }
            installFile(file)
        }
    }

    private fun installFile(file: java.io.File) {
        viewModelScope.launch {
            _state.update { it.copy(installBusy = true, installStatus = context.getString(R.string.apk_checking)) }
            val info = withContext(Dispatchers.IO) { inspector.inspect(file) }
            if (info == null) {
                _state.update { it.copy(installBusy = false, installStatus = context.getString(R.string.error_not_apk)) }
                return@launch
            }
            _state.update { it.copy(installStatus = context.getString(R.string.sideload_installing_named, info.label)) }

            when (val result = installer.install(file, info.packageName)) {
                is ApkInstaller.Result.Installed -> {
                    _state.update { it.copy(installBusy = false, installStatus = null) }
                    refreshApps(force = true)
                    // Give the package manager a moment to publish the new activity.
                    delay(600)
                    val installed = catalog.installed(force = true)
                        .firstOrNull { it.packageName == info.packageName }
                    if (installed != null) {
                        _state.update { it.copy(addPrompt = AddPrompt(installed)) }
                    }
                    if (info.compat == TvCompat.TOUCH_ONLY) {
                        _state.update { it.copy(compatPrompt = CompatPrompt(info)) }
                    }
                }

                is ApkInstaller.Result.NeedsPermission -> {
                    _state.update {
                        it.copy(installBusy = false, permissionNeeded = true, installStatus = null)
                    }
                }

                is ApkInstaller.Result.Failed -> _state.update {
                    it.copy(installBusy = false, installStatus = result.message)
                }

                else -> _state.update { it.copy(installBusy = false) }
            }
        }
    }

    fun confirmAddPrompt() {
        val prompt = _state.value.addPrompt ?: return
        viewModelScope.launch {
            if (prompt.entry.packageName !in _state.value.selected) store.toggleSelected(prompt.entry.packageName)
            store.markSeen(prompt.entry.packageName)
            _state.update { it.copy(addPrompt = null) }
        }
    }

    fun dismissAddPrompt() {
        val prompt = _state.value.addPrompt
        _state.update { it.copy(addPrompt = null) }
        prompt?.let { pending -> viewModelScope.launch { store.markSeen(pending.entry.packageName) } }
    }

    fun dismissCompatPrompt() = _state.update { it.copy(compatPrompt = null) }

    fun requestInstallPermission() {
        _state.update { it.copy(permissionNeeded = false) }
        installer.permissionSettingsIntent()?.let { intent ->
            runCatching { context.startActivity(intent) }
        } ?: showToast(context.getString(R.string.error_installs_blocked))
    }

    fun clearInstallStatus() = _state.update { it.copy(installStatus = null) }

    private fun copyToCache(uri: Uri): java.io.File? = runCatching {
        val name = "install-${System.currentTimeMillis()}.apk"
        val target = java.io.File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (target.length() < 1024) {
            target.delete()
            return null
        }
        target
    }.getOrNull()

    // ---- Streaming -------------------------------------------------------------------------------

    fun connectNuvio(email: String, password: String) {
        viewModelScope.launch {
            val result = streaming.connect(
                "nuvio",
                StreamingServiceAdapter.Credentials.Password(email, password),
            )
            when (result) {
                is StreamingServiceAdapter.AuthResult.Success ->
                    showToast(context.getString(R.string.settings_nuvio_connected, result.profileName))

                StreamingServiceAdapter.AuthResult.Invalid -> showToast(context.getString(R.string.error_nuvio_signin))
                StreamingServiceAdapter.AuthResult.NeedsTwoFactor -> showToast(context.getString(R.string.error_nuvio_2fa))
                is StreamingServiceAdapter.AuthResult.NetworkError -> showToast(result.message)
            }
        }
    }

    fun disconnectNuvio() {
        viewModelScope.launch {
            streaming.disconnect("nuvio")
            store.setNuvio(server = _state.value.settings.nuvioServer, account = null)
            showToast(context.getString(R.string.nuvio_disconnected))
        }
    }

    fun refreshStreaming() {
        viewModelScope.launch {
            streaming.refresh(force = true)
            showToast(context.getString(R.string.refreshed))
        }
    }

    // ---- Remote ----------------------------------------------------------------------------------

    private fun syncRemote(settings: LauncherSettings) {
        if (settings.remoteEnabled) {
            startRemote(settings.remotePort)
        } else if (remoteStarted) {
            stopRemote()
        }
    }

    private fun startRemote(port: Int) {
        if (remoteStarted) return
        RemoteService.start(context, port)
        remoteStarted = true
        _state.update {
            it.copy(remoteRunning = RemoteHub.running.value, uploadUrl = RemoteHub.httpUrl())
        }
        viewModelScope.launch {
            // The service binds asynchronously; report what actually came up.
            delay(400)
            RemoteHub.refreshPairingCode()
            _state.update {
                it.copy(
                    remoteRunning = RemoteHub.running.value,
                    pairingCode = RemoteHub.pairingCode.value,
                    uploadUrl = RemoteHub.httpUrl(),
                )
            }
        }
    }

    private fun stopRemote() {
        RemoteService.stop(context)
        RemoteHub.stop()
        remoteStarted = false
        _state.update { it.copy(remoteRunning = false, remoteClients = 0) }
    }

    private fun remoteState(): RemoteState {
        val current = _state.value
        val item = current.rows.getOrNull(current.focus.row)?.items?.getOrNull(current.focus.col)
        return RemoteState(
            screen = when (current.screen) {
                Screen.HOME -> if (current.overlay == Overlay.QUICK_SETTINGS) "overlay" else "home"
                Screen.SEARCH -> "search"
                Screen.SETTINGS, Screen.MANAGE_APPS, Screen.PAIRING, Screen.SIDELOAD -> "settings"
            },
            focusKind = when (item) {
                is HomeItem.App -> "app"
                is HomeItem.Media -> "media"
                else -> null
            },
            focusLabel = item?.title,
            media = null,
            volume = inputRouter.volumeFraction(),
            muted = inputRouter.isMuted(),
            pointer = current.pointerMode && current.cursorVisible,
            pin = current.screen == Screen.PAIRING,
        )
    }

    private fun remoteApps(): List<RemoteApp> = _state.value.selectedApps.map { entry ->
        RemoteApp(
            packageName = entry.packageName,
            name = entry.label,
            color = "#%06X".format(entry.accent and 0xFFFFFF),
            favourite = entry.packageName in _state.value.pins,
        )
    }

    // ---- RemoteInputSink ------------------------------------------------------------------------

    override fun onKey(key: Protocol.RemoteKey, hold: Boolean): Boolean {
        noteActivity()
        return when (key) {
            Protocol.RemoteKey.UP -> consume { if (_state.value.saverActive) stopSaver() else moveFocus(Direction.UP) }
            Protocol.RemoteKey.DOWN -> consume { if (_state.value.saverActive) stopSaver() else moveFocus(Direction.DOWN) }
            Protocol.RemoteKey.LEFT -> consume { moveFocus(Direction.LEFT) }
            Protocol.RemoteKey.RIGHT -> consume { moveFocus(Direction.RIGHT) }
            Protocol.RemoteKey.OK -> consume { activateFocus() }
            Protocol.RemoteKey.BACK -> back()
            Protocol.RemoteKey.HOME -> { goHome(); true }
            Protocol.RemoteKey.SEARCH -> { openSearch(); true }
            Protocol.RemoteKey.SETTINGS -> { openSettings(); true }
            Protocol.RemoteKey.MENU -> { toggleQuickSettings(); true }
            Protocol.RemoteKey.INFO -> { togglePointerMode(); true }
            else -> inputRouter.handleKey(key, hold)
        }
    }

    /** Runs a lambda and reports that the launcher consumed the key. */
    private inline fun consume(block: () -> Unit): Boolean {
        block()
        return true
    }

    override fun onPointer(dx: Float, dy: Float) {
        val current = _state.value
        if (!current.pointerMode) togglePointerMode(true)
        val x = (current.cursorX + dx * screenWidth * POINTER_GAIN).coerceIn(0f, screenWidth)
        val y = (current.cursorY + dy * screenHeight * POINTER_GAIN).coerceIn(0f, screenHeight)
        _state.update { it.copy(cursorX = x, cursorY = y, cursorVisible = true) }
        lastInputAt = System.currentTimeMillis()

        val target = cursorRegistry?.targetAt(androidx.compose.ui.geometry.Offset(x, y))
        if (target != null && target.id != hovered?.id) {
            hovered = target
            runCatching { target.onFocus() }
        }
    }

    override fun onClick() {
        noteActivity()
        val target = hovered
        if (target != null) {
            runCatching { target.onActivate() }
            return
        }
        // Nothing under the cursor (the status row, or a gap): fall back to the focused item.
        activateFocus()
    }

    override fun onLongPress() {
        noteActivity()
        hovered?.onLongPress?.let { runCatching(it) }
    }

    override fun onScroll(dy: Float) {
        noteActivity()
        moveFocus(if (dy > 0) Direction.RIGHT else Direction.LEFT)
    }

    override fun onText(text: String) {
        noteActivity()
        if (_state.value.screen != Screen.SEARCH) openSearch(text) else setQuery(_state.value.query + text)
    }

    override fun onBackspace(count: Int) {
        val query = _state.value.query
        if (query.isNotEmpty()) setQuery(query.dropLast(count.coerceAtLeast(1)))
    }

    override fun onLaunch(packageName: String): Boolean {
        noteActivity()
        val entry = _state.value.apps.firstOrNull { it.packageName == packageName }
            ?: _state.value.apps.firstOrNull { it.packageName == packageName + ".debug" }
            ?: return false
        if (packageName !in _state.value.selected) return false
        launchApp(entry)
        return true
    }

    override fun onCommand(action: String, arg: String?): Boolean = when (action) {
        "quicksettings" -> { toggleQuickSettings(); true }
        "screensaver" -> { startSaver(); true }
        "search" -> { openSearch(arg); true }
        "home" -> { goHome(); true }
        "pointer" -> { togglePointerMode(); true }
        "refresh" -> { refreshStreaming(); true }
        else -> false
    }

    /** The icon drawable for a package, already cached by the catalog. */
    fun icon(packageName: String): android.graphics.drawable.Drawable? = catalog.icon(packageName)

    override fun iconFor(packageName: String): ByteArray? = runCatching {
        val drawable = catalog.icon(packageName) ?: return null
        val bitmap = drawable.toBitmap(96, 96)
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }.getOrNull()

    // ---- Idle, installs, lifecycle ---------------------------------------------------------------

    fun setScreenSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            screenWidth = width.toFloat()
            screenHeight = height.toFloat()
        }
    }

    /** Any input, from any source, resets the screensaver clock. */
    fun noteActivity() {
        lastInputAt = System.currentTimeMillis()
        if (_state.value.saverActive && _state.value.overlay == null) {
            _state.update { it.copy(saverActive = false) }
        }
        if (_state.value.cursorVisible && !_state.value.pointerMode) {
            _state.update { it.copy(cursorVisible = false) }
        }
    }

    private fun startIdleWatcher() {
        idleWatcher?.cancel()
        idleWatcher = viewModelScope.launch {
            while (true) {
                delay(5_000)
                val current = _state.value
                val delayMs = current.settings.saverDelayMinutes * 60_000L
                val idle = System.currentTimeMillis() - lastInputAt
                if (!current.saverActive && current.screen == Screen.HOME && current.overlay == null &&
                    idle > delayMs
                ) {
                    _state.update { it.copy(saverActive = true) }
                }
                if (current.cursorVisible && idle > 4_000) {
                    _state.update { it.copy(cursorVisible = false) }
                }
            }
        }
    }

    /** New installs, uninstalls and updates, for the context.getString(R.string.new_app_title) banner. */
    private fun watchInstalls() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                catalog.cachedApps() // warm the cache before the rescan
                refreshApps(force = true)
                RemoteHub.broadcastApps()
            }
        }
        installWatcher = receiver
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { /* Without the broadcast we still rescan on resume. */ }
    }

    fun onResume() {
        noteActivity()
        refreshNetwork()
        refreshApps(force = false)
        viewModelScope.launch { streaming.refresh() }
        _state.update { it.copy(uploadUrl = RemoteHub.httpUrl()) }
    }

    override fun onCleared() {
        idleWatcher?.cancel()
        installWatcher?.let { runCatching { context.unregisterReceiver(it) } }
        RemoteHub.sink = null
        super.onCleared()
    }

    companion object {
        const val ACTION_SEARCH = "search"
        const val ACTION_QUICK = "quick"
        const val ACTION_SETTINGS = "settings"
        const val ACTION_ADD_APPS = "add_apps"
        const val ACTION_ADD_NEW = "add_new"
        const val ACTION_DISMISS_NEW = "dismiss_new"

        /** A little faster than a finger on glass; 1.0 matches the TV's pixels one to one. */
        private const val POINTER_GAIN = 1.15f

        fun percent(progress: Float?): Int = ((progress ?: 0f) * 100).roundToInt()
    }
}
