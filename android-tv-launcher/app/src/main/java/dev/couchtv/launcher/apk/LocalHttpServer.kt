package dev.couchtv.launcher.apk

import android.content.Context
import dev.couchtv.launcher.util.NetworkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The launcher's small HTTP surface, on one port:
 *
 *   GET  /          the APK upload page (a laptop's browser)
 *   POST /upload    the APK itself (multipart/form-data)
 *   GET  /info      JSON: this TV's name, id, protocol version, whether a code is showing
 *   GET  /icon?pkg= PNG app icon for the remote apps' shortcut list
 *   GET  /health    liveness, for the remote to test a manual address
 *
 * A tiny HTTP server so an APK can be dropped onto the TV from a laptop's browser.
 *
 * Installing an APK on a TV usually means a USB stick or a cloud drive on a remote-controlled
 * keyboard. This is the shortcut: the launcher shows `http://<tv-ip>:8770`, you open it on a computer
 * on the same Wi-Fi, drag the file into the page, and the launcher starts the install.
 *
 * Deliberately plain `ServerSocket` + one page of HTML: no framework, no dependency, ~4 KB of code.
 * Every request is read with strict limits, writes only ever land in the app's own cache folder, and
 * only files that end in `.apk` are accepted.
 */
class LocalHttpServer(
    private val context: Context,
    private val port: Int = 8770,
    private val onUploaded: (File) -> Unit,
    /** JSON for `GET /info`: the remote apps use it to confirm the TV and show its name. */
    private val infoProvider: () -> String = { """{"ok":true}""" },
    /** PNG bytes for `GET /icon?pkg=…`, or null for 404. */
    private val iconProvider: (String) -> ByteArray? = { null },
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

    fun url(): String? {
        val address = NetworkInfo.localAddress() ?: return null
        return "http://$address:$port"
    }

    /** Binds and starts accepting. Returns false if the port is taken (another instance, usually). */
    fun start(): Boolean {
        if (running.get()) return true
        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        }.getOrNull() ?: return false

        serverSocket = socket
        running.set(true)
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = worker
        acceptJob = worker.launch {
            while (isActive && running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { serve(client) }
            }
        }
        return true
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        acceptJob?.cancel()
        scope?.cancel()
        serverSocket = null
        acceptJob = null
        scope = null
    }

    private fun serve(socket: Socket) {
        socket.use { connection ->
            connection.soTimeout = 20_000
            runCatching {
                val input = BufferedInputStream(connection.getInputStream())
                val requestLine = readLine(input) ?: return@runCatching
                val parts = requestLine.split(' ')
                if (parts.size < 2) return@runCatching
                val method = parts[0].uppercase(Locale.ROOT)
                val path = parts[1].substringBefore('?')

                // Headers are bounded: a phone is not going to send a megabyte of them.
                val headers = mutableMapOf<String, String>()
                var headerLines = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    if (++headerLines > 64) return@runCatching
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
                    }
                }

                val output = connection.getOutputStream()
                when {
                    method == "GET" && (path == "/" || path == "/upload") -> sendPage(output)
                    method == "GET" && path == "/info" -> sendBody(output, infoProvider(), "application/json")
                    method == "GET" && path == "/icon" -> {
                        val packageName = queryParameter(parts[1], "pkg")
                        val bytes = packageName?.let(iconProvider)
                        if (bytes == null) {
                            sendJson(output, """{"ok":false,"error":"No icon"}""", status = "404 Not Found")
                        } else {
                            sendBody(output, bytes, "image/png", cache = "public, max-age=86400")
                        }
                    }
                    method == "GET" && path == "/health" -> sendJson(output, """{"ok":true}""")
                    method == "POST" && path == "/upload" -> {
                        val length = headers["content-length"]?.toLongOrNull() ?: 0L
                        if (length <= 0L || length > MAX_UPLOAD) {
                            sendJson(output, """{"ok":false,"error":"Too large"}""", status = "413 Payload Too Large")
                            return@runCatching
                        }
                        val boundary = boundaryOf(headers["content-type"]) ?: run {
                            sendJson(output, """{"ok":false,"error":"Expected multipart/form-data"}""", status = "400 Bad Request")
                            return@runCatching
                        }
                        val saved = receive(input, length, boundary)
                        if (saved == null) {
                            sendJson(output, """{"ok":false,"error":"No APK in that request"}""", status = "400 Bad Request")
                        } else {
                            output.write(
                                """
                                HTTP/1.1 200 OK
                                Content-Type: application/json
                                Connection: close

                                {"ok":true,"name":"${saved.name}"}
                                """.trimIndent().toByteArray()
                            )
                            output.flush()
                            onUploaded(saved)
                        }
                    }

                    else -> sendJson(output, """{"ok":false,"error":"Not found"}""", status = "404 Not Found")
                }
            }
        }
    }

    /** Copies the multipart body, writing the first `.apk` part to the cache and skipping the rest. */
    private fun receive(input: InputStream, length: Long, boundary: String): File? {
        val folder = uploadFolder()
        var saved: File? = null
        var target: OutputStream? = null
        try {
            val delimiter = "--$boundary".toByteArray()
            // Everything up to the first boundary is preamble; drop it, and the rest of that line.
            skipToBoundary(input, delimiter)
            readLine(input)
            var remaining = length

            while (remaining > 0) {
                // Part headers.
                var headerBytes = 0L
                var fileName: String? = null
                while (true) {
                    val line = readLine(input) ?: return saved
                    remaining -= line.length + 2
                    headerBytes += line.length + 2
                    if (headerBytes > 16_384) return saved
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Disposition", ignoreCase = true)) {
                        fileName = fileNameOf(line)
                    }
                }

                val isApk = fileName?.lowercase(Locale.ROOT)?.endsWith(".apk") == true
                val destination = if (isApk) {
                    File(folder, sanitise(fileName!!)).also { candidate ->
                        saved = candidate
                        target = candidate.outputStream().buffered()
                    }
                } else {
                    null
                }

                val copied = copyUntilBoundary(input, target, delimiter)
                remaining -= copied
                target?.close()
                target = null
                if (destination != null && (destination.length() == 0L || copied < 22L)) {
                    // Far too small to be a zip; drop it rather than offering a broken APK.
                    destination.delete()
                    if (saved == destination) saved = null
                }

                // After a boundary come either "--" (end) or CRLF and another part.
                val terminator = ByteArray(2)
                val read = input.read(terminator)
                remaining -= read
                if (read <= 0 || (terminator[0] == '-'.code.toByte() && terminator[1] == '-'.code.toByte())) break
                // The two bytes read were CRLF; the loop continues with the next part's headers.
            }
        } finally {
            runCatching { target?.close() }
        }
        return saved
    }

    private fun uploadFolder(): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).resolve("uploads").apply { mkdirs() }

    /** Skips ahead to the first boundary, byte by byte (bounded by the body length upstream). */
    private fun skipToBoundary(input: InputStream, delimiter: ByteArray) {
        var matched = 0
        while (matched < delimiter.size) {
            val byte = input.read()
            if (byte < 0) return
            matched = if (byte.toByte() == delimiter[matched]) matched + 1 else 0
        }
    }

    /** Copies bytes to [out] (or discards) until `CRLF--boundary` is found; returns bytes consumed. */
    private fun copyUntilBoundary(input: InputStream, out: OutputStream?, delimiter: ByteArray): Long {
        val marker = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()) + delimiter
        val window = ByteArray(marker.size)
        var filled = 0
        var copied = 0L
        while (true) {
            val byte = input.read()
            if (byte < 0) return copied
            copied++
            // Fill the lookahead window, then check whether it is the boundary.
            if (filled < window.size) {
                window[filled++] = byte.toByte()
                if (filled == window.size && matches(window, marker)) {
                    // Everything in the window was the marker: write nothing.
                    return copied
                }
                // Not (yet) the marker: flush the oldest byte, slide by one.
                if (filled == window.size) {
                    out?.write(window[0].toInt())
                    System.arraycopy(window, 1, window, 0, window.size - 1)
                    filled--
                }
            }
        }
    }

    private fun matches(window: ByteArray, marker: ByteArray): Boolean {
        if (window.size != marker.size) return false
        for (index in marker.indices) if (window[index] != marker[index]) return false
        return true
    }

    private fun boundaryOf(contentType: String?): String? {
        val header = contentType ?: return null
        val marker = "boundary="
        val at = header.indexOf(marker, ignoreCase = true)
        if (at < 0) return null
        return header.substring(at + marker.length).trim().trim('"').takeIf { it.isNotBlank() }
    }

    private fun fileNameOf(disposition: String): String? {
        val marker = "filename="
        val at = disposition.indexOf(marker, ignoreCase = true)
        if (at < 0) return null
        return disposition.substring(at + marker.length)
            .trim()
            .substringBefore(';')
            .trim()
            .trim('"')
            .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .takeIf { it.isNotBlank() }
    }

    /** Never trust a browser's filename: no paths, no surprises. */
    private fun sanitise(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(96)
        return if (cleaned.lowercase(Locale.ROOT).endsWith(".apk")) cleaned else "$cleaned.apk"
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString().trimEnd('\r')
            if (builder.length > 8192) return builder.toString()
            builder.append(byte.toChar())
        }
    }

    private fun sendPage(output: OutputStream) {
        val body = pageHtml().toByteArray()
        output.write(
            buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray()
        )
        output.write(body)
        output.flush()
    }

    /** Sends pre-built bytes (an icon, or a JSON document) with a correct content type. */
    private fun sendBody(
        output: OutputStream,
        body: ByteArray,
        contentType: String,
        cache: String = "no-store",
        status: String = "200 OK",
    ) {
        output.write(
            buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Cache-Control: $cache\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray()
        )
        output.write(body)
        output.flush()
    }

    private fun sendBody(output: OutputStream, body: String, contentType: String) =
        sendBody(output, body.toByteArray(), contentType)

    /** Reads one query parameter from a request target like `/icon?pkg=com.example`. */
    private fun queryParameter(target: String, key: String): String? {
        val query = target.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=')
                if (name != key) null else pair.substringAfter('=', "")
            }
            .firstOrNull()
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun sendJson(output: OutputStream, json: String, status: String = "200 OK") {
        val body = json.toByteArray()
        output.write(
            buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray()
        )
        output.write(body)
        output.flush()
    }

    /** The upload page: the same dark glass as the TV, one drop zone, one progress bar. */
    private fun pageHtml(): String = """
<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Couch TV · Install an APK</title>
<style>
 :root{--bg:#07080b;--fg:#fff;--dim:rgba(255,255,255,.62);--line:rgba(255,255,255,.14);
   --accent:#3ddc84;--glass:rgba(255,255,255,.07)}
 *{box-sizing:border-box}
 body{margin:0;min-height:100dvh;display:grid;place-items:center;background:
   radial-gradient(70vmax 60vmax at 10% 0%,rgba(70,110,255,.22),transparent 70%),
   radial-gradient(60vmax 60vmax at 90% 80%,rgba(0,190,170,.12),transparent 70%),var(--bg);
   color:var(--fg);font:16px/1.5 -apple-system,system-ui,Roboto,sans-serif;padding:24px}
 main{width:100%;max-width:520px}
 h1{margin:0 0 6px;font-size:26px;letter-spacing:-.02em}
 p{margin:0 0 22px;color:var(--dim)}
 .drop{display:grid;place-items:center;gap:10px;padding:52px 24px;border-radius:24px;
   border:2px dashed var(--line);background:var(--glass);text-align:center;transition:border-color .2s,background .2s;cursor:pointer}
 .drop.hot{border-color:var(--accent);background:rgba(61,220,132,.08)}
 .drop b{font-size:18px}
 .drop span{color:var(--dim);font-size:14px}
 .bar{height:8px;border-radius:99px;background:rgba(255,255,255,.12);margin-top:18px;overflow:hidden;display:none}
 .bar i{display:block;height:100%;width:0;background:var(--accent);transition:width .15s}
 .msg{margin-top:16px;font-size:14px;color:var(--dim);min-height:20px}
 .msg.ok{color:var(--accent)}.msg.err{color:#ff6b6b}
 input{display:none}
</style></head>
<body><main>
 <h1>Install an APK</h1>
 <p>Drop a file here, or choose one. It is sent straight to your TV on this network.</p>
 <label class="drop" id="drop">
   <b>Drop your APK</b>
   <span>or click to choose a file</span>
   <input type="file" id="file" accept=".apk,application/vnd.android.package-archive">
 </label>
 <div class="bar" id="bar"><i id="fill"></i></div>
 <div class="msg" id="msg"></div>
</main>
<script>
 const drop=document.getElementById('drop'),file=document.getElementById('file'),
       bar=document.getElementById('bar'),fill=document.getElementById('fill'),msg=document.getElementById('msg');
 const say=(text,kind)=>{msg.textContent=text;msg.className='msg'+(kind?' '+kind:'')};
 const upload=(f)=>{
   if(!f){return}
   if(!/\.apk$/i.test(f.name)){say('That is not an APK.','err');return}
   const form=new FormData();form.append('apk',f,f.name);
   const xhr=new XMLHttpRequest();xhr.open('POST','/upload');
   bar.style.display='block';say('Uploading ' + f.name + '…');
   xhr.upload.onprogress=(e)=>{if(e.lengthComputable){fill.style.width=(e.loaded/e.total*100)+'%'}};
   xhr.onload=()=>{
     bar.style.display='none';fill.style.width='0';
     try{const r=JSON.parse(xhr.responseText);
       if(r.ok){say('Sent to the TV. Confirm the install on your TV screen.','ok')}
       else{say(r.error||'The TV refused that file.','err')}}
     catch(_){say('The TV did not answer.','err')}
   };
   xhr.onerror=()=>{bar.style.display='none';say('Connection lost. Is the TV still on this network?','err')};
   xhr.send(form);
 };
 drop.addEventListener('dragover',(e)=>{e.preventDefault();drop.classList.add('hot')});
 drop.addEventListener('dragleave',()=>drop.classList.remove('hot'));
 drop.addEventListener('drop',(e)=>{e.preventDefault();drop.classList.remove('hot');upload(e.dataTransfer.files[0])});
 file.addEventListener('change',()=>upload(file.files[0]));
</script>
</body></html>
""".trimIndent()

    private companion object {
        const val MAX_UPLOAD = 512L * 1024 * 1024
    }
}
