import type { Metadata } from 'next';
import Link from 'next/link';
import Reveal from '@/components/Reveal';
import QrCode from '@/components/QrCode';
import InlineText from '@/components/InlineText';
import { downloads, site } from '@/lib/site';

export const metadata: Metadata = {
  title: 'Download',
  description:
    'Download Couch TV for Android TV, the Couch Remote for iPhone, iPad or Android, and the original Mac TV app. Free, open source, and built from this repository.',
  alternates: { canonical: '/download' },
};

const steps = [
  ['Install the launcher', 'adb install, the upload page, or a USB stick. Any of the three works.'],
  ['Choose apps', 'The first launch is the picker: tick what you want on the home screen.'],
  ['Set it as Home', 'Press Home and pick Couch TV, or set it over adb once.'],
  ['Pair a remote', 'Settings › Phone remote shows a six-digit code. The phone finds the TV by name.'],
];

export default function DownloadPage() {
  return (
    <>
      <section className="mx-auto max-w-wrap px-4 pb-4 pt-14 sm:px-6 sm:pt-20">
        <p className="label text-phosphor">Download</p>
        <h1 className="display mt-3 max-w-3xl text-[clamp(2.2rem,5.4vw,4rem)]">
          Four things, all free, all in one repository.
        </h1>
        <p className="mt-5 max-w-2xl text-lg leading-relaxed text-dim">
          Every build links to the release page, because that is where CI puts the artefacts. Nothing
          is tracked: no download counter of ours, no email wall, no installer that phones home.
        </p>
      </section>

      <section className="mx-auto max-w-wrap px-4 py-10 sm:px-6">
        <div className="grid gap-4 lg:grid-cols-2">
          {downloads.map((item, index) => (
            <Reveal key={item.id} delay={index * 0.04}>
              <article className="flex h-full flex-col rounded-panel border border-line/12 bg-panel/50 p-6 sm:p-7">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="label text-faint">{item.platform}</p>
                    <h2 className="mt-2 text-xl font-semibold text-ink">{item.app}</h2>
                  </div>
                  <span className="shrink-0 rounded-full border border-line/15 px-2.5 py-1 text-[11px] font-medium text-dim">
                    {item.badge}
                  </span>
                </div>

                <p className="mt-3 text-sm leading-relaxed text-dim">{item.blurb}</p>

                <ul className="mt-4 flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-faint">
                  {item.requirements.map((requirement) => (
                    <li key={requirement} className="flex items-center gap-1.5">
                      <span className="h-1 w-1 rounded-full bg-phosphor/70" />
                      {requirement}
                    </li>
                  ))}
                </ul>

                <div className="mt-auto flex flex-wrap items-end justify-between gap-4 pt-6">
                  <div>
                    <a
                      href={item.href}
                      className="inline-block rounded-full bg-ink px-5 py-2.5 text-sm font-semibold text-canvas transition-transform hover:-translate-y-0.5"
                    >
                      {item.cta}
                    </a>
                    {item.note && (
                      <p className="mt-2 max-w-xs text-[11px] leading-relaxed text-faint">
                        <InlineText text={item.note} />
                      </p>
                    )}
                  </div>
                  {item.qr && <QrCode value={item.href} size={92} label="Scan for the release page" />}
                </div>
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="border-y border-line/10 bg-panel/40">
        <div className="mx-auto max-w-wrap px-4 py-16 sm:px-6">
          <div className="grid gap-8 md:grid-cols-[minmax(0,0.8fr)_minmax(0,1fr)] md:gap-14">
            <div>
              <p className="label text-phosphor">On the TV</p>
              <h2 className="display mt-3 text-[clamp(1.7rem,3.4vw,2.4rem)]">Four steps, once.</h2>
              <p className="mt-4 text-sm leading-relaxed text-dim">
                The full guide covers adb, USB sticks, blocked installs, mDNS on guest networks and
                what to do when a code is rejected.
              </p>
              <Link
                href="/docs#setup"
                className="mt-5 inline-block rounded-full border border-line/20 px-4 py-2 text-sm font-semibold text-ink hover:border-phosphor/60"
              >
                Setup guide
              </Link>
            </div>
            <ol className="space-y-3">
              {steps.map(([title, body], index) => (
                <li key={title} className="flex gap-4 rounded-xl border border-line/12 bg-canvas p-4">
                  <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full border border-phosphor/40 font-mono text-xs text-phosphor">
                    {index + 1}
                  </span>
                  <span>
                    <span className="block text-sm font-semibold text-ink">{title}</span>
                    <span className="mt-0.5 block text-sm text-dim">{body}</span>
                  </span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-wrap px-4 py-16 sm:px-6">
        <div className="grid gap-6 md:grid-cols-3">
          <div className="rounded-2xl border border-line/12 bg-panel/50 p-6">
            <h2 className="text-sm font-semibold text-ink">Build it yourself</h2>
            <p className="mt-2 text-sm leading-relaxed text-dim">
              JDK 17 and the Android SDK are the whole toolchain. The website and the docs live in the
              same checkout.
            </p>
            <pre className="mt-4 overflow-x-auto rounded-xl border border-line/12 bg-canvas p-3 font-mono text-[11px] leading-relaxed text-ink">
{`cd android-tv-launcher
gradle :app:assembleDebug
gradle :app:testDebugUnitTest`}
            </pre>
          </div>
          <div className="rounded-2xl border border-line/12 bg-panel/50 p-6">
            <h2 className="text-sm font-semibold text-ink">Verify the download</h2>
            <p className="mt-2 text-sm leading-relaxed text-dim">
              Releases carry a SHA-256 per artefact, and the APK is signed with the repository&apos;s
              key. Compare before installing:
            </p>
            <pre className="mt-4 overflow-x-auto rounded-xl border border-line/12 bg-canvas p-3 font-mono text-[11px] leading-relaxed text-ink">
{`sha256sum couch-tv-1.0.0.apk
apksigner verify --print-certs \\
  couch-tv-1.0.0.apk`}
            </pre>
          </div>
          <div className="rounded-2xl border border-line/12 bg-panel/50 p-6">
            <h2 className="text-sm font-semibold text-ink">No Play Store</h2>
            <p className="mt-2 text-sm leading-relaxed text-dim">
              Being a launcher with sideloading means the Play Store is not a good fit, and the
              project would rather not depend on it. Sideloading is the supported path on a TV
              anyway.
            </p>
            <a
              href={site.issues}
              className="mt-4 inline-block rounded-full border border-line/20 px-4 py-2 text-xs font-semibold text-ink hover:border-phosphor/60"
            >
              Ask a question
            </a>
          </div>
        </div>
      </section>
    </>
  );
}
