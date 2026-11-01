/**
 * All the copy and links the site needs, in one place, so a page is layout and nothing else.
 *
 * Links point at the repository and its releases: every download on this site is built from the
 * source in that repo, and nothing is hosted anywhere else.
 */

export const site = {
  name: 'Couch TV',
  family: 'Mac TV',
  tagline: 'Turn any screen into a smart TV',
  description:
    'The Mac TV launcher now has an Android sibling: a fast, quiet home screen for Android TV, tablets and phones, with an app picker, APK sideloading, your streaming library, and two companion remotes.',
  repo: 'https://github.com/amiralif22/mac-tv',
  releases: 'https://github.com/amiralif22/mac-tv/releases',
  issues: 'https://github.com/amiralif22/mac-tv/issues',
  license: 'https://github.com/amiralif22/mac-tv/blob/main/LICENSE',
  docs: 'https://github.com/amiralif22/mac-tv/tree/main/shared',
  url: process.env.NEXT_PUBLIC_SITE_URL ?? 'https://couch-tv.vercel.app',
} as const;

export const nav = [
  { href: '/', label: 'Home' },
  { href: '/features', label: 'Features' },
  { href: '/download', label: 'Download' },
  { href: '/docs', label: 'Docs' },
] as const;

export type Download = {
  id: string;
  platform: string;
  app: string;
  blurb: string;
  requirements: string[];
  href: string;
  cta: string;
  badge: string;
  qr?: boolean;
  note?: string;
};

export const downloads: Download[] = [
  {
    id: 'android-tv',
    platform: 'Android TV · tablet · phone',
    app: 'Couch TV Launcher',
    blurb:
      'The home screen itself. Pick your apps on first launch, sideload APKs, add your streaming library, and drive it all with a remote.',
    requirements: ['Android 7.0 (API 24) or newer', 'ARM or x86', 'Under 30 MB', 'Works on TV, tablet and phone'],
    href: `${site.releases}`,
    cta: 'Download APK',
    badge: 'Free · open source',
    qr: true,
    note: 'Grab the newest `couch-tv-*.apk` from the release assets, or build it with Gradle.',
  },
  {
    id: 'remote-android',
    platform: 'Android phone · tablet',
    app: 'Couch Remote (Android)',
    blurb:
      'A pointer remote for the phone in your pocket: D-pad, LG-style touchpad cursor, keyboard, voice search and app shortcuts.',
    requirements: ['Android 7.0 (API 24) or newer', 'Same Wi-Fi as the TV'],
    href: `${site.releases}`,
    cta: 'Download APK',
    badge: 'Free · open source',
    qr: true,
  },
  {
    id: 'remote-ios',
    platform: 'iPhone · iPad',
    app: 'Couch Remote (iOS)',
    blurb:
      'The same remote in SwiftUI, for iOS 15 and later. Build it in Xcode in a minute, or sideload the release build.',
    requirements: ['iOS 15 or newer', 'Same Wi-Fi as the TV'],
    href: `${site.releases}`,
    cta: 'Get the build',
    badge: 'SwiftUI · iOS 15+',
    qr: true,
  },
  {
    id: 'mac-tv',
    platform: 'macOS 14 or newer',
    app: 'Mac TV',
    blurb:
      'The original: your Mac as an Apple TV, with streaming in their official web players, movies, and a phone remote. Unchanged by this release.',
    requirements: ['macOS 14 Sonoma or newer', 'Apple silicon or Intel', 'Universal binary'],
    href: `${site.releases}`,
    cta: 'Download for Mac',
    badge: 'Original app',
    note: 'Existing installs update themselves. The download page of the Mac app still applies.',
  },
];

export const features = [
  {
    title: 'Cross-platform, one design',
    body: 'The Android launcher is a sibling, not a port: the same canvas, the same tvOS type scale, the same glass and the same white focus platter, rebuilt in Compose.',
    glyph: 'screens',
  },
  {
    title: 'Choose your apps, once',
    body: 'A first-run picker lists every installed app, grouped by category. Only what you tick reaches the home screen — and Manage Apps is the same picker.',
    glyph: 'check',
  },
  {
    title: 'Smart APK sideloading',
    body: 'Pick an APK on the TV, or drop one from a laptop at http://tv-ip:8770. Couch TV reads the build, installs it through the system installer, and offers to add it.',
    glyph: 'download',
  },
  {
    title: 'It knows what a remote can do',
    body: 'Each app is checked for a leanback launcher entry, a TV banner and the Leanback library. Touch-only builds get a quiet badge and an offer to swap.',
    glyph: 'remote',
  },
  {
    title: 'Your streaming library, on the home screen',
    body: 'Connect Nuvio and Continue Watching, Trending, Movies and Series come from your own account — cached on disk, deep-linked into the official app.',
    glyph: 'play',
  },
  {
    title: 'LG-style pointer remote',
    body: 'Drag on your phone and a 60 Hz cursor moves on the TV, focus following underneath it. Tap to click, two fingers to scroll, keyboard and voice included.',
    glyph: 'pointer',
  },
  {
    title: 'Bluetooth remotes and controllers',
    body: 'Every screen is reachable with a D-pad: arrows, Enter and Back. Game controllers and TV remotes arrive as ordinary key events and just work.',
    glyph: 'dpad',
  },
  {
    title: 'Fast, quiet, and yours',
    body: 'First frame under half a second from cache, 60 fps rows, no account of ours, no analytics, no content hosted. Nothing about what you watch leaves the house.',
    glyph: 'bolt',
  },
];

export const comparison = {
  columns: ['Mac TV (macOS)', 'Couch TV (Android)', 'Couch Remote (phone)'],
  rows: [
    { label: 'Apple TV–style home screen', values: [true, true, false] },
    { label: 'Choose your apps on first launch', values: [true, true, false] },
    { label: 'Set as the device home screen', values: [true, true, false] },
    { label: 'Streaming in official apps', values: ['Web players', 'Official Android apps', '—'] },
    { label: 'Continue Watching from Nuvio', values: [false, true, false] },
    { label: 'Install APKs from the TV', values: [false, true, false] },
    { label: 'TV-compatibility detection', values: [false, true, false] },
    { label: 'Phone remote over Wi-Fi', values: ['In a browser', 'Companion app', 'The app'] },
    { label: 'Pointer / touchpad mode', values: [true, true, true] },
    { label: 'Screen saver with aerials', values: ['Apple aerials', 'Drifting colour', '—'] },
    { label: 'Local files and network shares', values: [true, false, false] },
    { label: 'Works offline', values: [true, 'Cached rows', true] },
    { label: 'Open source', values: [true, true, true] },
  ],
};

export const faq = [
  {
    q: 'Is this a separate app from Mac TV?',
    a: 'Yes. Mac TV stays exactly as it is: a macOS app. Couch TV is a new Android application in the same repository, sharing the design language and the remote protocol, not a fork of the Mac code.',
  },
  {
    q: 'Does the app picker really start empty?',
    a: 'It does. Nothing is selected by default, and the home screen only ever draws apps you ticked. A first-run install with nothing chosen shows an empty row with an "Add apps" tile.',
  },
  {
    q: 'How do I get it onto a TV whose only input is a remote?',
    a: 'Three ways: `adb install` over the network, the upload page at http://tv-ip:8770 from a laptop, or a USB stick plus any file manager. The upload page is the one most people use.',
  },
  {
    q: 'Will my phone find the TV by itself?',
    a: 'On the same Wi-Fi, yes — the TV advertises `_couchremote._tcp` over mDNS. If your network blocks multicast (some guest networks do), type the address the TV shows in Settings › Phone remote.',
  },
  {
    q: 'Does the remote control Netflix on the TV?',
    a: 'The remote fully controls Couch TV. Inside another app, Android does not allow a normal app to inject D-pad keys; with the optional accessibility service, the pointer can tap and scroll, and Back/Home work. Volume and wake always work — see Troubleshooting.',
  },
  {
    q: 'What does the streaming integration do with my account?',
    a: 'It signs in to the service you connect, reads your library metadata, and caches it on the TV so the home screen paints instantly. It never downloads, proxies or plays anything; tapping a title opens the official app.',
  },
  {
    q: 'Is any data sent anywhere?',
    a: 'No. There is no account, no server of ours and no analytics in the Android app. Streaming requests go to the service you connected; everything else stays on the device.',
  },
  {
    q: 'Can I use it on a tablet instead of a TV?',
    a: 'Yes. It is a launcher, so it can be the home screen of a tablet or a phone dock. Layout adapts: tiles are larger, labels are always visible, and touch is first class.',
  },
];

export type DocSection = {
  id: string;
  title: string;
  intro: string;
  steps?: { title: string; body: string; code?: string }[];
};

export const docs: DocSection[] = [
  {
    id: 'setup',
    title: 'Set up the launcher',
    intro:
      'Couch TV is a home screen. The first launch is the only time it asks a question; after that, it opens straight to your apps.',
    steps: [
      {
        title: 'Install the APK',
        body: 'On a TV with network debugging: `adb connect <tv-ip>:5555` then `adb install -r couch-tv.apk`. Without adb, use the upload page (below) or a USB stick.',
        code: 'adb install -r app/build/outputs/apk/debug/app-debug.apk',
      },
      {
        title: 'Choose your apps',
        body: 'On first launch, every installed app appears, grouped by category. Tick what you want, use "Select all" if you would rather start full, then press "Set up my home screen".',
      },
      {
        title: 'Make it the home screen',
        body: 'Press Home and choose Couch TV and "Always". On a box without that prompt, set it from a terminal.',
        code: 'adb shell cmd package set-home-activity dev.couchtv.launcher/.MainActivity',
      },
      {
        title: 'Pair a remote',
        body: 'Open Settings › Phone remote on the TV. It shows a six-digit code. Open Couch Remote on your phone; the TV appears by name, and the code confirms it.',
      },
    ],
  },
  {
    id: 'sideload',
    title: 'Sideload an APK',
    intro:
      'Two ways in, both local. Android will ask once to allow installs from Couch TV; that is the system installer, not the launcher asking for trust.',
    steps: [
      {
        title: 'From the TV',
        body: 'Settings › Install an APK › Choose APK file. Picking a file from a USB stick is the simplest path on a box with no browser.',
      },
      {
        title: 'From a computer on the same network',
        body: 'Open the address shown on the TV (http://tv-ip:8770) and drop the APK on the page. The launcher starts the install and asks whether to add the app.',
      },
      {
        title: 'Replacing a touch-only app',
        body: 'If a build has no leanback entry and no TV banner, Couch TV says so and offers the Android TV version. Download that APK and install it over the top — same package, same data.',
      },
      {
        title: 'If the install is blocked',
        body: 'The system blocks installs from unknown sources per app. Settings › Apps › Couch TV › Install unknown apps. The launcher links straight there.',
      },
    ],
  },
  {
    id: 'streaming',
    title: 'Connect a streaming service',
    intro:
      'Adapters read a signed-in user\'s own library and deep-link into the official app. Nuvio is the first; the interface is designed so another service is one file.',
    steps: [
      {
        title: 'Connect Nuvio',
        body: 'Settings › Nuvio › sign in with your Nuvio account. The token is kept in the Android Keystore; sign out from the same row.',
      },
      {
        title: 'What appears',
        body: 'Continue Watching at the top, then Trending, then your apps, then Recommended movies and series. Everything is cached, so a cold start paints before the network answers.',
      },
      {
        title: 'Opening a title',
        body: 'Tapping a poster opens Nuvio at that item. If Nuvio is not installed, the launcher offers the Play Store and the sideload screen instead of failing.',
      },
      {
        title: 'Pointing at your own server',
        body: 'Endpoints live in `NuvioEndpoints` in the launcher; a self-hosted deployment only needs that base URL.',
        code: 'data class NuvioEndpoints(val base: String = "https://api.nuvio.app/api/v1")',
      },
    ],
  },
  {
    id: 'remote',
    title: 'Pair and use the remote',
    intro:
      'One WebSocket carries input, state and pairing. The protocol is documented once in `shared/remote-protocol.md` and implemented by the TV, the Android app and the iOS app.',
    steps: [
      {
        title: 'Discovery',
        body: 'The TV advertises `_couchremote._tcp` on the LAN with its id, name and protocol version. Phones list what they find; nothing is typed.',
      },
      {
        title: 'Pairing',
        body: 'The first connection asks for the six-digit code. A token (32 characters, stored as a SHA-256 digest on the TV) is returned once, rotated on every handshake.',
      },
      {
        title: 'Touchpad mode',
        body: 'Drag to move the cursor; it arrives as 60 Hz relative deltas and is integrated on the TV, so packet loss cannot make it jump. Tap clicks, two fingers scroll.',
      },
      {
        title: 'Keyboard and voice',
        body: 'Text is sent as text and transcribed on the phone, so no audio crosses the network. On the launcher it lands in search.',
        code: '{ "t": "text", "v": "the bear" }',
      },
    ],
  },
  {
    id: 'adapters',
    title: 'Write a streaming adapter',
    intro:
      'A new service is one class and one registration line. The launcher owns rows, caching, focus and errors; the adapter owns the service.',
    steps: [
      {
        title: 'Implement the interface',
        body: 'Extend `StreamingServiceAdapter` with authentication, Continue Watching, recommendations, and a deep link. Every failure is a value, never a thrown exception.',
        code: 'class MyServiceAdapter(...) : StreamingServiceAdapter {\n  override val id = "myservice"\n  override suspend fun getContinueWatching(limit: Int): List<MediaItem> = ...\n}',
      },
      {
        title: 'Register it',
        body: 'Add it to `StreamingHub.builtIn`. Nothing in the UI changes: rows appear when the adapter can fill them.',
        code: 'listOf(NuvioAdapter(context, credentials) { baseUrl() }, MyServiceAdapter(...))',
      },
      {
        title: 'Test the mapper',
        body: 'Adapters must be forgiving. A fixture through the mapper, asserting `MediaItem`s, is the expected test (see `NuvioAdapterTest`).',
      },
      {
        title: 'Respect the rules',
        body: 'No scraping, no content hosting, no secrets outside the keystore, and no identifiers in logs. See `shared/streaming-adapters.md`.',
      },
    ],
  },
  {
    id: 'troubleshooting',
    title: 'Troubleshooting',
    intro:
      'The honest list, including the two things Android genuinely does not allow.',
    steps: [
      {
        title: 'The phone cannot find the TV',
        body: 'Guest Wi-Fi often blocks multicast. Type the address from Settings › Phone remote into the app instead; the port is shown beside it.',
      },
      {
        title: 'The pairing code is rejected',
        body: 'Codes rotate every five minutes, and five wrong tries block pairing for ten minutes. Press "New code" on the TV and try again.',
      },
      {
        title: 'D-pad does not work inside Netflix',
        body: 'Android does not let one app inject key events into another. Use the TV\'s own remote for other apps, or turn on the accessibility service to tap and scroll with the pointer.',
      },
      {
        title: 'Screen off does nothing',
        body: 'Turning a screen off needs device-owner rights. Set Couch TV as device owner on a fresh box (`adb shell dpm set-device-owner dev.couchtv.launcher/.remote.RemoteDeviceAdmin`), or use the TV\'s power button.',
      },
      {
        title: 'Streaming rows are empty',
        body: 'Sign in under Settings › Nuvio, then Refresh recommendations. Rows older than a day are dropped on purpose so a stale account cannot look current.',
      },
      {
        title: 'An app is missing from the picker',
        body: 'Only packages with a launcher entry are listed. Work-profile apps and some system apps have none; that is Android, not the launcher.',
      },
    ],
  },
];
