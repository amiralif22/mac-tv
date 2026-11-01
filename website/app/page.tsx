import type { Metadata } from 'next';
import Link from 'next/link';
import TvMockup from '@/components/TvMockup';
import Reveal from '@/components/Reveal';
import Icon from '@/components/Icon';
import PickerDemo from '@/components/PickerDemo';
import InlineText from '@/components/InlineText';
import { comparison, features, site } from '@/lib/site';

export const metadata: Metadata = {
  title: `${site.family} · ${site.tagline}`,
  description: site.description,
  alternates: { canonical: '/' },
};

const marks = [
  ['60 fps', 'rows and focus'],
  ['< 500 ms', 'to first frame'],
  ['0', 'accounts, trackers'],
  ['27 MB', 'the whole launcher'],
];

export default function HomePage() {
  return (
    <>
      {/* Hero */}
      <section className="mx-auto max-w-wrap px-4 pb-8 pt-14 sm:px-6 sm:pt-20">
        <div className="mx-auto max-w-3xl text-center">
          <p className="label inline-flex items-center gap-2 rounded-full border border-line/15 px-3 py-1.5 text-dim">
            <span className="h-1.5 w-1.5 rounded-full bg-phosphor" />
            Mac TV, now on the big screen
          </p>
          <h1 className="display mt-5 text-[clamp(2.5rem,6.2vw,4.7rem)]">
            Your TV, but only
            <br />
            the apps you chose.
          </h1>
          <p className="mx-auto mt-5 max-w-xl text-lg leading-relaxed text-dim">
            <strong className="font-semibold text-ink">Couch TV</strong> is a fast, quiet home screen
            for Android TV, tablets and phones. Pick your apps on first launch, sideload what is
            missing, and put your streaming library where you can reach it.
          </p>

          <div className="mt-7 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <a
              href={site.releases}
              className="w-full rounded-full bg-ink px-6 py-3.5 text-sm font-semibold text-canvas transition-transform hover:-translate-y-0.5 sm:w-auto"
            >
              Download Couch TV
            </a>
            <Link
              href="/features"
              className="w-full rounded-full border border-line/20 px-6 py-3.5 text-sm font-semibold text-ink transition-colors hover:border-phosphor/60 sm:w-auto"
            >
              See what it does
            </Link>
          </div>

          <p className="mt-4 text-xs text-faint">
            Open source, MIT · Android 7.0+ · No account, nothing to subscribe to
          </p>
        </div>

        <div className="mx-auto mt-12 max-w-5xl sm:mt-16">
          <TvMockup />
        </div>

        <dl className="mx-auto mt-14 grid max-w-4xl grid-cols-2 gap-px overflow-hidden rounded-2xl border border-line/12 bg-line/10 sm:grid-cols-4">
          {marks.map(([value, label]) => (
            <div key={label} className="bg-canvas px-4 py-5 text-center">
              <dt className="display text-2xl text-ink sm:text-3xl">{value}</dt>
              <dd className="mt-1 text-xs text-faint">{label}</dd>
            </div>
          ))}
        </dl>
      </section>

      {/* The picker */}
      <section className="mx-auto max-w-wrap px-4 py-20 sm:px-6" id="picker">
        <Reveal>
          <div className="max-w-2xl">
            <p className="label text-phosphor">First launch</p>
            <h2 className="display mt-3 text-[clamp(1.9rem,4vw,3rem)]">
              A home screen that starts empty.
            </h2>
            <p className="mt-4 text-base leading-relaxed text-dim">
              Every installed app appears in one grid, grouped by category, with Select all and
              Deselect all. Tick six apps or tick sixty. Nothing reaches the home screen that you did
              not tick, and Settings › Manage Apps is the same screen again, so changing your mind
              costs nothing.
            </p>
          </div>
        </Reveal>

        <Reveal delay={0.05}>
          <div className="mt-9">
            <PickerDemo />
          </div>
        </Reveal>
      </section>

      {/* Feature grid */}
      <section className="border-y border-line/10 bg-panel/40">
        <div className="mx-auto max-w-wrap px-4 py-20 sm:px-6">
          <Reveal>
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div className="max-w-xl">
                <p className="label text-phosphor">Everything else</p>
                <h2 className="display mt-3 text-[clamp(1.9rem,4vw,3rem)]">
                  Small details, every one deliberate.
                </h2>
              </div>
              <Link
                href="/features"
                className="label shrink-0 text-dim underline-offset-8 hover:text-ink hover:underline"
              >
                All features →
              </Link>
            </div>
          </Reveal>

          <div className="mt-10 grid gap-px overflow-hidden rounded-panel border border-line/12 bg-line/10 sm:grid-cols-2 lg:grid-cols-3">
            {features.slice(0, 6).map((feature, index) => (
              <Reveal key={feature.title} delay={index * 0.04} className="bg-canvas">
                <div className="h-full p-6 sm:p-7">
                  <span className="grid h-10 w-10 place-items-center rounded-xl border border-line/12 text-phosphor">
                    <Icon name={feature.glyph} />
                  </span>
                  <h3 className="mt-4 text-base font-semibold text-ink">{feature.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-dim">
                    <InlineText text={feature.body} />
                  </p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Remote */}
      <section className="mx-auto max-w-wrap px-4 py-20 sm:px-6">
        <div className="grid items-center gap-10 lg:grid-cols-2">
          <Reveal>
            <p className="label text-phosphor">The phone you already hold</p>
            <h2 className="display mt-3 text-[clamp(1.9rem,4vw,3rem)]">
              A pointer remote
              <br />
              that arrives instantly.
            </h2>
            <p className="mt-4 text-base leading-relaxed text-dim">
              The TV advertises itself over mDNS; the phone finds it, confirms a six-digit code once,
              and remembers it. From then on the remote is one tap away — and the touchpad moves a
              real cursor on the TV at 60 Hz, focus following underneath it.
            </p>
            <ul className="mt-6 space-y-3 text-sm text-dim">
              {[
                ['D-pad, OK, Back and Home', 'Arrows and Enter, exactly where your thumb expects them'],
                ['Touchpad with a visible cursor', 'Relative deltas, integrated on the TV — no cursor jumps'],
                ['Keyboard and voice', 'Typed and transcribed on the phone; only text crosses the network'],
                ['Transport, volume, power', 'Play, pause, seek, mute and sleep, without leaving the sofa'],
              ].map(([title, body]) => (
                <li key={title} className="flex gap-3">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-phosphor" />
                  <span>
                    <span className="font-medium text-ink">{title}</span>
                    <span className="block text-dim">{body}</span>
                  </span>
                </li>
              ))}
            </ul>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link
                href="/download"
                className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-canvas"
              >
                Get the remotes
              </Link>
              <Link
                href="/docs#remote"
                className="rounded-full border border-line/20 px-5 py-3 text-sm font-semibold text-ink hover:border-phosphor/60"
              >
                How pairing works
              </Link>
            </div>
          </Reveal>

          <Reveal delay={0.06}>
            <div className="relative overflow-hidden rounded-panel border border-line/12 bg-panel/60 p-6">
              <div className="flex items-start justify-center gap-6">
                {/* Phone with touchpad */}
                <div className="w-[150px] rounded-[26px] border border-line/15 bg-[#0b0d12] p-2.5 shadow-lift sm:w-[176px]">
                  <div className="mx-auto mb-2 h-1 w-10 rounded-full bg-white/20" />
                  <div className="rounded-[16px] bg-[#0f1219] p-2.5">
                    <div className="flex items-center justify-between text-[7px] text-white/40">
                      <span className="truncate">Living room TV</span>
                      <span className="rounded-full bg-phosphor/20 px-1.5 py-0.5 text-phosphor">60 Hz</span>
                    </div>
                    <div className="relative mt-2 grid aspect-square place-items-center rounded-full border border-white/10 bg-white/[0.03]">
                      <div className="absolute h-[58%] w-[58%] rounded-full border border-white/10" />
                      <div className="absolute left-[46%] top-[38%] h-2.5 w-2.5 rounded-full bg-white shadow-[0_0_10px_2px_rgba(255,255,255,0.5)]" />
                      <span className="absolute bottom-1.5 text-[6px] uppercase tracking-[0.2em] text-white/30">
                        drag to point
                      </span>
                    </div>
                    <div className="mt-2 grid grid-cols-3 gap-1">
                      {['⏮', '⏯', '⏭'].map((glyph) => (
                        <span
                          key={glyph}
                          className="grid h-6 place-items-center rounded-md bg-white/[0.06] text-[9px] text-white/70"
                        >
                          {glyph}
                        </span>
                      ))}
                    </div>
                    <div className="mt-1 grid grid-cols-2 gap-1">
                      <span className="grid h-6 place-items-center rounded-md bg-white/[0.06] text-[7px] text-white/60">
                        ⌨ Keyboard
                      </span>
                      <span className="grid h-6 place-items-center rounded-md bg-white/[0.06] text-[7px] text-white/60">
                        🎙 Voice
                      </span>
                    </div>
                  </div>
                </div>

                {/* The TV side: cursor over the home screen */}
                <div className="hidden w-[210px] overflow-hidden rounded-xl border border-line/12 bg-[#07080b] sm:block">
                  <div className="p-3">
                    <p className="text-[8px] uppercase tracking-[0.2em] text-white/35">Your apps</p>
                    <div className="mt-2 grid grid-cols-3 gap-1.5">
                      {[152, 355, 205, 0, 122, 42].map((hue, index) => (
                        <div
                          key={hue}
                          className="relative overflow-hidden rounded-md"
                          style={{
                            aspectRatio: '5 / 3',
                            background: `linear-gradient(150deg, hsl(${hue} 45% 30%), hsl(${hue} 58% 14%))`,
                            boxShadow:
                              index === 1
                                ? '0 0 0 1.5px rgba(255,255,255,0.9)'
                                : 'inset 0 0 0 1px rgba(255,255,255,0.07)',
                          }}
                        />
                      ))}
                    </div>
                    {/* The cursor, exactly where a drag would leave it. */}
                    <div className="relative h-10">
                      <svg
                        className="absolute left-[58%] top-3 drop-shadow-[0_2px_6px_rgba(0,0,0,0.8)]"
                        width="16"
                        height="22"
                        viewBox="0 0 16 22"
                        aria-hidden
                      >
                        <path d="M1 1 14 12l-5.6 1.2L6 21z" fill="#fff" stroke="#06070a" strokeWidth="1.2" />
                      </svg>
                    </div>
                  </div>
                </div>
              </div>

              <p className="mt-5 text-xs leading-relaxed text-faint">
                Both remotes are open source: SwiftUI for iOS 15+, Compose for Android 7+. The TV
                keeps a token digest, never the token itself, and every connection can be revoked
                from Settings.
              </p>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Comparison */}
      <section className="border-y border-line/10 bg-panel/40">
        <div className="mx-auto max-w-wrap px-4 py-20 sm:px-6">
          <Reveal>
            <div className="max-w-xl">
              <p className="label text-phosphor">One family</p>
              <h2 className="display mt-3 text-[clamp(1.9rem,4vw,3rem)]">
                Mac TV is unchanged. This sits beside it.
              </h2>
              <p className="mt-4 text-base leading-relaxed text-dim">
                The Mac app still does what it always did, and not one file of it was touched. Couch
                TV is a second, independent app that borrows the design language and the remote
                protocol.
              </p>
            </div>
          </Reveal>

          <Reveal delay={0.05}>
            <div className="mt-9 overflow-x-auto rounded-panel border border-line/12">
              <table className="w-full min-w-[640px] border-collapse text-sm">
                <caption className="sr-only">Feature comparison between Mac TV, Couch TV and the remote</caption>
                <thead>
                  <tr className="bg-raised/60">
                    <th scope="col" className="label px-4 py-3 text-left text-faint">
                      Capability
                    </th>
                    {comparison.columns.map((column) => (
                      <th key={column} scope="col" className="px-4 py-3 text-left text-xs font-semibold text-ink">
                        {column}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {comparison.rows.map((row) => (
                    <tr key={row.label} className="border-t border-line/10">
                      <th scope="row" className="px-4 py-3 text-left font-normal text-dim">
                        {row.label}
                      </th>
                      {row.values.map((value, index) => (
                        <td key={index} className="px-4 py-3 text-dim">
                          {value === true ? (
                            <span className="text-phosphor" aria-label="Yes">
                              ●
                            </span>
                          ) : value === false ? (
                            <span className="text-faint" aria-label="No">
                              ○
                            </span>
                          ) : (
                            <span className="text-xs">{value}</span>
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Closing CTA */}
      <section className="mx-auto max-w-wrap px-4 py-20 sm:px-6">
        <Reveal>
          <div className="relative overflow-hidden rounded-panel border border-line/12 bg-panel/50 px-6 py-12 text-center sm:px-12">
            <div
              className="pointer-events-none absolute inset-0 opacity-60"
              style={{
                backgroundImage:
                  'radial-gradient(60% 80% at 50% 0%, rgb(var(--phosphor) / 0.12), transparent 70%)',
              }}
            />
            <div className="relative mx-auto max-w-xl">
              <h2 className="display text-[clamp(1.8rem,4vw,2.8rem)]">Five minutes, then never again.</h2>
              <p className="mt-4 text-base leading-relaxed text-dim">
                Install the APK, tick the apps you actually use, set it as Home. The launcher then
                gets out of the way — that is the entire point.
              </p>
              <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
                <a
                  href={site.releases}
                  className="rounded-full bg-ink px-6 py-3.5 text-sm font-semibold text-canvas"
                >
                  Download
                </a>
                <Link
                  href="/docs"
                  className="rounded-full border border-line/20 px-6 py-3.5 text-sm font-semibold text-ink hover:border-phosphor/60"
                >
                  Read the setup guide
                </Link>
              </div>
            </div>
          </div>
        </Reveal>
      </section>
    </>
  );
}
