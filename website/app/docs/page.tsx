import type { Metadata } from 'next';
import Link from 'next/link';
import Reveal from '@/components/Reveal';
import { docs, faq, site } from '@/lib/site';

export const metadata: Metadata = {
  title: 'Docs',
  description:
    'How to install Couch TV, set it as your home screen, sideload an APK, connect Nuvio, pair a phone remote, and write your own streaming adapter.',
  alternates: { canonical: '/docs' },
};

export default function DocsPage() {
  return (
    <div className="mx-auto max-w-wrap px-4 pb-10 pt-14 sm:px-6 sm:pt-20">
      <div className="max-w-2xl">
        <p className="label text-phosphor">Documentation</p>
        <h1 className="display mt-3 text-[clamp(2.1rem,5vw,3.6rem)]">From a pile of APKs to a home screen.</h1>
        <p className="mt-5 text-base leading-relaxed text-dim">
          Six sections, each with the command that does the thing. If a step depends on your hardware,
          it says so. Nothing here asks you to sign in to anything of ours.
        </p>
      </div>

      <div className="mt-10 grid gap-10 lg:grid-cols-[210px_minmax(0,1fr)] lg:gap-14">
        <aside className="lg:sticky lg:top-24 lg:h-fit">
          <p className="label mb-3 text-faint">On this page</p>
          <nav aria-label="Documentation sections">
            <ol className="space-y-1.5">
              {docs.map((section) => (
                <li key={section.id}>
                  <a
                    href={`#${section.id}`}
                    className="block rounded-lg px-2 py-1.5 text-sm text-dim transition-colors hover:bg-raised/60 hover:text-ink"
                  >
                    {section.title}
                  </a>
                </li>
              ))}
              <li>
                <a
                  href="#faq"
                  className="block rounded-lg px-2 py-1.5 text-sm text-dim transition-colors hover:bg-raised/60 hover:text-ink"
                >
                  FAQ
                </a>
              </li>
            </ol>
          </nav>
          <div className="mt-6 rounded-xl border border-line/12 bg-panel/50 p-4">
            <p className="text-xs leading-relaxed text-dim">
              The wire format is documented separately in{' '}
              <a
                href={`${site.repo}/blob/main/shared/remote-protocol.md`}
                className="font-medium text-ink underline decoration-phosphor/50 underline-offset-4"
              >
                shared/remote-protocol.md
              </a>
              .
            </p>
          </div>
        </aside>

        <div className="prose-couch max-w-none">
          {docs.map((section) => (
            <Reveal key={section.id}>
              <section id={section.id} className="scroll-mt-24 border-t border-line/10 pt-8 first:border-0 first:pt-0">
                <h2 className="!mt-0">{section.title}</h2>
                <p>{section.intro}</p>
                {section.steps && (
                  <ol className="mt-5 space-y-4">
                    {section.steps.map((step, index) => (
                      <li key={step.title} className="rounded-xl border border-line/12 bg-panel/40 p-4">
                        <p className="!mb-1 flex items-center gap-2 text-sm font-semibold text-ink">
                          <span className="font-mono text-[11px] font-normal text-phosphor">
                            {String(index + 1).padStart(2, '0')}
                          </span>
                          {step.title}
                        </p>
                        <p className="!mb-0 text-sm">{step.body}</p>
                        {step.code && <pre className="!mb-0 mt-3">
                          <code>{step.code}</code>
                        </pre>}
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            </Reveal>
          ))}

          <section id="faq" className="scroll-mt-24 border-t border-line/10 pt-8">
            <h2>FAQ</h2>
            <div className="mt-4 divide-y divide-line/10 rounded-xl border border-line/12 bg-panel/40">
              {faq.map((item) => (
                <details key={item.q} className="group px-4 py-3 [&_summary::-webkit-details-marker]:hidden">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-sm font-medium text-ink">
                    {item.q}
                    <span className="text-faint transition-transform group-open:rotate-45" aria-hidden>
                      +
                    </span>
                  </summary>
                  <p className="!mb-0 !mt-2 text-sm">{item.a}</p>
                </details>
              ))}
            </div>
          </section>

          <section className="border-t border-line/10 pt-8">
            <h2>Still stuck?</h2>
            <p>
              Open an issue with the TV model, the Android version and what the screen said. The
              launcher keeps a plain text log you can copy from Settings › Diagnostics.
            </p>
            <div className="flex flex-wrap gap-3">
              <a
                href={site.issues}
                className="rounded-full bg-ink px-5 py-2.5 text-sm font-semibold !text-canvas"
              >
                Open an issue
              </a>
              <Link
                href="/download"
                className="rounded-full border border-line/20 px-5 py-2.5 text-sm font-semibold !text-ink"
              >
                Back to downloads
              </Link>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
