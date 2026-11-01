'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import ThemeToggle from './ThemeToggle';
import { nav, site } from '@/lib/site';

/**
 * A sticky, translucent bar. On a phone it collapses to a burger; the menu closes on navigation and
 * on Escape, because a menu that traps you on a TV-related site is a bad joke.
 */
export default function Nav() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <nav className="sticky top-0 z-40 border-b border-line/10 bg-canvas/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-wrap items-center justify-between gap-4 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2.5" aria-label={`${site.family} home`}>
          <Wordmark />
          <span className="label text-ink">{site.family}</span>
        </Link>

        <div className="hidden items-center gap-7 md:flex">
          {nav.map((item) => {
            const active = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? 'page' : undefined}
                className={`label transition-colors ${
                  active ? 'text-phosphor' : 'text-dim hover:text-ink'
                }`}
              >
                {item.label}
              </Link>
            );
          })}
          <ThemeToggle />
          <a
            href={site.releases}
            className="rounded-full bg-ink px-4 py-2 text-sm font-semibold text-canvas transition-transform hover:-translate-y-0.5"
          >
            Download
          </a>
        </div>

        <div className="flex items-center gap-2 md:hidden">
          <ThemeToggle />
          <button
            type="button"
            aria-expanded={open}
            aria-controls="mobile-menu"
            aria-label={open ? 'Close menu' : 'Open menu'}
            onClick={() => setOpen((value) => !value)}
            className="grid h-9 w-9 place-items-center rounded-full text-ink hairline"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
              {open ? (
                <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              ) : (
                <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              )}
            </svg>
          </button>
        </div>
      </div>

      {open && (
        <div id="mobile-menu" className="border-t border-line/10 bg-canvas px-4 pb-5 pt-3 md:hidden">
          <div className="flex flex-col gap-1">
            {nav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="rounded-lg px-3 py-3 text-base font-medium text-ink hover:bg-raised"
              >
                {item.label}
              </Link>
            ))}
            <a
              href={site.releases}
              className="mt-2 rounded-full bg-ink px-4 py-3 text-center text-sm font-semibold text-canvas"
            >
              Download
            </a>
          </div>
        </div>
      )}
    </nav>
  );
}

/** The app icon in SVG: a charcoal set, a glass screen, the phosphor glow and a brass foot. */
function Wordmark() {
  return (
    <svg width="30" height="30" viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="8" fill="#0A0B0E" />
      <rect x="4.5" y="8" width="23" height="15" rx="3" fill="#2B2A2E" />
      <rect x="6.5" y="10" width="19" height="11" rx="1.6" fill="#0A0B0E" />
      <path d="M13.5 12.6 20 15.5l-6.5 2.9z" fill="#3DDC84" />
      <rect x="13" y="24.5" width="6" height="1.8" rx="0.9" fill="#C69A52" />
    </svg>
  );
}
