import type { Metadata } from 'next';
import Link from 'next/link';
import Reveal from '@/components/Reveal';
import Icon from '@/components/Icon';
import InlineText from '@/components/InlineText';
import { features, site } from '@/lib/site';

export const metadata: Metadata = {
  title: 'Features',
  description:
    'Everything in Couch TV: the first-launch app picker, APK sideloading over the network, TV-compatibility detection, streaming rows from your own account, a pointer remote and a screen saver.',
  alternates: { canonical: '/features' },
};

const sections = [
  {
    eyebrow: 'The home screen',
    title: 'Rows that contain only your things',
    body: 'A status row, pins, Continue Watching, Trending, your apps, then recommended films and series, then notifications if you asked for them. A row with nothing in it is not drawn — including all of them, which is what a fresh install looks like before you choose.',
    points: [
      'Focus memory: coming back from an app puts you where you left, not at the top',
      'The backdrop is the focused item, blurred and slowly drifting — never a slideshow of things you do not own',
      'Long-press any tile to pin it; pins gather in their own row and survive reboots',
      'Posters carry progress bars, episode numbers and time remaining when the service knows them',
    ],
  },
  {
    eyebrow: 'Layout',
    title: 'Lean-back on a TV, touch-first on a tablet',
    body: 'The launcher reads the device: an app with a leanback launcher entry, or a TV banner, or a remotes-only input set, gets the TV layout — larger tiles, labels only on focus, five rows of overscan-safe margins. A tablet gets bigger type and permanent labels.',
    points: [
      'All sizes are derived from one 1920×1080 reference canvas, so a 1080p TV and a 4K panel look identical',
      'Aspect is assumed 16:9; the settings screen lets you override the diagonal if a panel reports nonsense',
      'Every screen is reachable with arrows, Enter and Back — no screen depends on touch',
      'D-pad focus is a grid engine with wrapping, row memories and a single animated platter',
    ],
  },
  {
    eyebrow: 'Apps and APKs',
    title: 'Sideloading without a laptop, and an honest warning',
    body: 'Pick an APK from internal storage or a USB stick, or open the launcher\'s upload page on any computer on the same Wi-Fi and drop the file there. The install goes through Android\'s own session installer, so nothing is silently replaced.',
    points: [
      'The upload page lists only what it received; files are deleted once installed',
      'Inspection reads the APK before install: leanback intent, TV banner, Leanback library references',
      'Touch-only builds get a small red badge and, once, an offer to install the Android TV version',
      'A device-local database remembers verdicts per package and version, so each app is judged once',
    ],
  },
  {
    eyebrow: 'Streaming',
    title: 'One interface, any service',
    body: 'Continue Watching, recommendations and deep links come from the service through a small adapter: authenticate, getContinueWatching, getRecommendations, getDeepLink, isInstalled. Nuvio is the reference implementation; another provider is one class and one registration line.',
    points: [
      'Credentials go in the Android Keystore, never in preferences or logs',
      'Responses are cached on disk and painted before the network answers, so a cold start is instant',
      'A stale cache is never shown as current: entries expire, and a failed refresh degrades to your apps',
      'If the service app is missing, the launcher offers the store and the sideload flow instead of failing',
    ],
  },
  {
    eyebrow: 'Remote',
    title: 'Phone remote with a cursor',
    body: 'The TV runs the server: mDNS advertising, a WebSocket for input and state, a six-digit code for the first pairing. The phones are clients, and both are in this repository.',
    points: [
      'Touchpad sends relative deltas; the TV integrates them at display rate, so a dropped packet cannot make the cursor jump',
      'The cursor is drawn once per frame, is visible on every screen, and hides after four seconds of stillness',
      'Volume, mute and wake work everywhere; screen-off works when Couch TV is device owner',
      'Keyboard text lands in search; voice is transcribed on the phone, so no audio leaves it',
    ],
  },
  {
    eyebrow: 'Comfort',
    title: 'Screen saver, quick settings, search',
    body: 'A slow drifting clock with your photos or pure colour after a delay you set. Long-press Home for quick settings: Wi-Fi, Bluetooth, brightness, volume, sleep timer. Type anywhere to search apps, films and series in one list.',
    points: [
      'Search is incremental and local-first: apps answer instantly, streaming results merge in when they arrive',
      'Parental controls: a six-digit PIN gates launching apps above a rating and hides restricted rows',
      'Notifications row renders what the notification listener is allowed to show — off by default',
      'Auto-start on boot is a setting, off by default, and uses a plain boot receiver',
    ],
  },
];

const numbers = [
  ['< 500 ms', 'Home on screen from a warm cache', 'The first frame is drawn from disk; network rows fill behind it.'],
  ['60 fps', 'Focus moves and row scrolling', 'One animated platter, no layout thrash, no blur recomputed per frame.'],
  ['~27 MB', 'Release APK', 'No bundled player, no analytics SDK, no web engine.'],
  ['< 150 MB', 'Peak memory', 'Posters are decoded at the size they are drawn, then evicted.'],
];

export default function FeaturesPage() {
  return (
    <>
      <section className="mx-auto max-w-wrap px-4 pb-6 pt-14 sm:px-6 sm:pt-20">
        <p className="label text-phosphor">Features</p>
        <h1 className="display mt-3 max-w-3xl text-[clamp(2.2rem,5.4vw,4rem)]">
          Everything it does, and the three things it refuses to do.
        </h1>
        <p className="mt-5 max-w-2xl text-lg leading-relaxed text-dim">
          No recommendations from us, no telemetry, no promoted row. What is here is a home screen
          that obeys: your apps, your library, your remote.
        </p>
      </section>

      <section className="mx-auto max-w-wrap px-4 py-10 sm:px-6">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((feature, index) => (
            <Reveal key={feature.title} delay={index * 0.03}>
              <div className="h-full rounded-2xl border border-line/12 bg-panel/50 p-5">
                <span className="grid h-9 w-9 place-items-center rounded-lg border border-line/12 text-phosphor">
                  <Icon name={feature.glyph} />
                </span>
                <h2 className="mt-3 text-sm font-semibold text-ink">{feature.title}</h2>
                <p className="mt-1.5 text-[13px] leading-relaxed text-dim">
                  <InlineText text={feature.body} />
                </p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      <div className="mx-auto max-w-wrap px-4 pb-10 sm:px-6">
        {sections.map((section) => (
          <Reveal key={section.title}>
            <section className="grid gap-6 border-t border-line/10 py-12 md:grid-cols-[minmax(0,0.85fr)_minmax(0,1fr)] md:gap-12">
              <div>
                <p className="label text-faint">{section.eyebrow}</p>
                <h2 className="display mt-3 text-[clamp(1.6rem,3vw,2.2rem)]">{section.title}</h2>
              </div>
              <div>
                <p className="text-base leading-relaxed text-dim">
                  <InlineText text={section.body} />
                </p>
                <ul className="mt-5 space-y-2.5">
                  {section.points.map((point) => (
                    <li key={point} className="flex gap-3 text-sm leading-relaxed text-dim">
                      <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-phosphor" />
                      {point}
                    </li>
                  ))}
                </ul>
              </div>
            </section>
          </Reveal>
        ))}
      </div>

      <section className="border-y border-line/10 bg-panel/40">
        <div className="mx-auto max-w-wrap px-4 py-16 sm:px-6">
          <p className="label text-phosphor">Measured, not claimed</p>
          <h2 className="display mt-3 max-w-2xl text-[clamp(1.7rem,3.4vw,2.6rem)]">
            The numbers the CI job enforces.
          </h2>
          <dl className="mt-8 grid gap-px overflow-hidden rounded-panel border border-line/12 bg-line/10 sm:grid-cols-2 lg:grid-cols-4">
            {numbers.map(([value, label, note]) => (
              <div key={label} className="bg-canvas p-6">
                <dt className="display text-2xl text-ink">{value}</dt>
                <dd className="mt-2 text-sm font-medium text-ink">{label}</dd>
                <dd className="mt-1 text-xs leading-relaxed text-faint">{note}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-5 text-xs text-faint">
            The APK size budget is checked in CI; the frame and memory targets are measured on a
            Chromecast with Google TV and a 2019 Shield, which is the slowest hardware worth
            supporting.
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-wrap px-4 py-16 sm:px-6">
        <div className="grid gap-6 md:grid-cols-2">
          <Reveal>
            <div className="h-full rounded-panel border border-line/12 bg-panel/50 p-7">
              <h2 className="text-lg font-semibold text-ink">What it deliberately does not do</h2>
              <ul className="mt-4 space-y-3 text-sm leading-relaxed text-dim">
                <li>No content hosting, proxying or downloading — tapping a title opens the official app.</li>
                <li>No scraping behind a login. Adapters use documented endpoints or a token you granted.</li>
                <li>No analytics, crash reporting or remote configuration of any kind.</li>
                <li>No network listener unless you turn the remote on, and no connection after you revoke it.</li>
              </ul>
            </div>
          </Reveal>
          <Reveal delay={0.05}>
            <div className="flex h-full flex-col justify-between rounded-panel border border-line/12 bg-panel/50 p-7">
              <div>
                <h2 className="text-lg font-semibold text-ink">And what Android will not allow</h2>
                <p className="mt-3 text-sm leading-relaxed text-dim">
                  A normal app cannot inject D-pad events into another app, and cannot turn the panel
                  off without device-owner rights. Both limits are documented, both have a supported
                  workaround, and neither is papered over with a button that silently fails.
                </p>
              </div>
              <div className="mt-6 flex flex-wrap gap-3">
                <Link
                  href="/docs#troubleshooting"
                  className="rounded-full border border-line/20 px-4 py-2 text-sm font-semibold text-ink hover:border-phosphor/60"
                >
                  Read the limits
                </Link>
                <a
                  href={`${site.repo}/tree/main/shared`}
                  className="rounded-full border border-line/20 px-4 py-2 text-sm font-semibold text-ink hover:border-phosphor/60"
                >
                  Protocol docs
                </a>
              </div>
            </div>
          </Reveal>
        </div>
      </section>
    </>
  );
}
