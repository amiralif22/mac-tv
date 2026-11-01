'use client';

import { useEffect, useMemo, useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';

/**
 * A TV drawn in CSS, showing the launcher's home screen. No screenshots, no image requests: the
 * hero is DOM, so it stays sharp on every display and costs nothing to load.
 *
 * Two behaviours mirror the real app: the focused tile expands and takes the white platter, and the
 * backdrop behind everything is the focused item's artwork, blurred and breathing slowly.
 */

type App = { name: string; hue: number; tag?: string; badge?: string };

const featured = {
  title: 'The Bear',
  meta: 'S3 E4 · 24 min left',
  progress: 0.68,
  hue: 18,
};

const apps: App[] = [
  { name: 'Nuvio', hue: 152 },
  { name: 'Netflix', hue: 355 },
  { name: 'Prime Video', hue: 205 },
  { name: 'YouTube', hue: 0, badge: '!' },
  { name: 'Spotify', hue: 122 },
  { name: 'Photos', hue: 42 },
];

const continueWatching = [
  { title: 'The Bear', subtitle: 'S3 E4 · 24 min left', progress: 0.68, hue: 18 },
  { title: 'Dune: Part Two', subtitle: '1 h 12 min left', progress: 0.34, hue: 32 },
  { title: 'Severance', subtitle: 'S2 E7 · 31 min left', progress: 0.52, hue: 205 },
  { title: 'Past Lives', subtitle: '48 min left', progress: 0.61, hue: 268 },
  { title: 'The Zone of Interest', subtitle: '1 h 39 min left', progress: 0.12, hue: 96 },
];

export default function TvMockup({ className = '' }: { className?: string }) {
  const reduce = useReducedMotion();
  const [focus, setFocus] = useState(1);
  const [clock, setClock] = useState('');

  // A real clock in the status row — the launcher shows the time, so the mockup does too.
  useEffect(() => {
    const paint = () =>
      setClock(
        new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }),
      );
    paint();
    const id = window.setInterval(paint, 30_000);
    return () => window.clearInterval(id);
  }, []);

  // Cycle focus every four seconds so the hero demonstrates the focus platter without an input.
  useEffect(() => {
    if (reduce) return;
    const id = window.setInterval(() => setFocus((value) => (value + 1) % apps.length), 4000);
    return () => window.clearInterval(id);
  }, [reduce]);

  const active = apps[focus];
  const backdrop = useMemo(
    () =>
      `radial-gradient(120% 90% at 18% 8%, hsl(${active.hue} 55% 26% / 0.55), transparent 62%),
       radial-gradient(90% 80% at 88% 30%, hsl(${(active.hue + 40) % 360} 60% 22% / 0.5), transparent 60%),
       linear-gradient(180deg, #10131b 0%, #07080b 70%)`,
    [active.hue],
  );

  return (
    <div className={`relative ${className}`}>
      {/* A stand, then the panel. */}
      <div className="mx-auto h-3 w-24 rounded-b-lg bg-gradient-to-b from-[#2a2b31] to-[#14151a]" />
      <div className="tv-bezel rounded-[26px] p-[10px] sm:rounded-[32px] sm:p-[13px]">
        <div className="relative aspect-video w-full overflow-hidden rounded-[16px] sm:rounded-[22px] tv-screen">
          {/* Backdrop art, blurred and drifting. */}
          <div
            className="absolute inset-0 transition-[background] duration-700 ease-out"
            style={{ backgroundImage: backdrop }}
          />
          <div
            className={`absolute inset-0 opacity-70 ${reduce ? '' : 'animate-drift'}`}
            style={{
              backgroundImage: `radial-gradient(45% 55% at 30% 65%, hsl(${active.hue} 70% 45% / 0.22), transparent 70%)`,
              filter: 'blur(24px)',
            }}
          />

          {/* Everything here is scaled off the panel width, like the real `tvDp`. */}
          <div className="absolute inset-0 flex flex-col p-[3.2%] text-white">
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2 sm:gap-3">
                <span className="grid h-6 w-6 place-items-center rounded-md bg-white/10 text-[10px] font-bold tracking-tight sm:h-7 sm:w-7">
                  CT
                </span>
                <span className="text-[10px] font-semibold uppercase tracking-[0.18em] text-white/55 sm:text-[11px]">
                  Couch TV
                </span>
              </div>
              <div className="flex items-center gap-3">
                <span className="hidden text-[10px] text-white/45 sm:block">Wi-Fi · HomePod</span>
                <span className="font-mono text-[10px] text-white/70 sm:text-[11px]">{clock || '21:14'}</span>
              </div>
            </div>

            {/* Featured item, exactly the size the app gives it. */}
            <div className="mt-[4.5%] max-w-[62%]">
              <p className="text-[9px] font-semibold uppercase tracking-[0.2em] text-phosphor sm:text-[10px]">
                Continue watching
              </p>
              <h3 className="mt-1 text-[clamp(14px,2.6vw,30px)] font-extrabold leading-none tracking-[-0.03em]">
                {featured.title}
              </h3>
              <p className="mt-1 hidden text-[10px] text-white/55 sm:block">{featured.meta}</p>
              <div className="mt-2 hidden gap-1.5 sm:flex">
                <span className="rounded-full bg-white px-2.5 py-1 text-[9px] font-semibold text-black">
                  Resume
                </span>
                <span className="rounded-full bg-white/12 px-2.5 py-1 text-[9px] font-semibold text-white/85">
                  Details
                </span>
              </div>
            </div>

            <div className="flex-1" />

            {/* The app row, with the white focus platter on the focused tile. */}
            <div className="flex items-end gap-[1.6%]">
              {apps.map((app, index) => {
                const isFocused = index === focus;
                return (
                  <div key={app.name} className="flex w-[13.5%] flex-col items-center gap-1">
                    <div
                      className="relative w-full overflow-hidden rounded-[10px] transition-transform duration-300"
                      style={{
                        aspectRatio: '5 / 3',
                        transform: isFocused ? 'scale(1.12)' : 'scale(1)',
                        background: `linear-gradient(150deg, hsl(${app.hue} 46% 34%), hsl(${app.hue} 58% 17%))`,
                        boxShadow: isFocused
                          ? '0 0 0 2px rgba(255,255,255,0.92), 0 14px 28px -12px rgba(0,0,0,0.9)'
                          : 'inset 0 0 0 1px rgba(255,255,255,0.08)',
                      }}
                    >
                      <div
                        className="absolute inset-0 opacity-70"
                        style={{
                          backgroundImage: `radial-gradient(60% 60% at 30% 25%, hsl(${app.hue} 70% 62% / 0.55), transparent 70%)`,
                        }}
                      />
                      <span className="absolute inset-x-0 bottom-0 truncate px-1.5 pb-1 text-[7px] font-semibold text-white/90 sm:text-[8px]">
                        {app.name}
                      </span>
                      {app.badge && (
                        <span className="absolute right-1 top-1 grid h-3.5 w-3.5 place-items-center rounded-full bg-[#FF453A] text-[7px] font-bold text-white">
                          {app.badge}
                        </span>
                      )}
                    </div>
                    <span
                      className={`truncate text-[7px] transition-colors sm:text-[8px] ${
                        isFocused ? 'text-white' : 'text-white/45'
                      }`}
                    >
                      {app.name}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* The next row peeks in, the way it does on a real overscan-safe home screen. */}
            <div className="mt-[2.2%] flex gap-[1.2%]">
              {continueWatching.map((item) => (
                <div key={item.title} className="w-[9.4%]">
                  <div
                    className="relative overflow-hidden rounded-[6px]"
                    style={{
                      aspectRatio: '2 / 3',
                      background: `linear-gradient(160deg, hsl(${item.hue} 40% 30%), hsl(${item.hue} 55% 12%))`,
                    }}
                  >
                    <div
                      className="absolute inset-0 opacity-60"
                      style={{
                        backgroundImage: `radial-gradient(70% 50% at 40% 20%, hsl(${item.hue} 70% 60% / 0.4), transparent 70%)`,
                      }}
                    />
                  </div>
                  <div className="progress-track mt-1 h-[2px] w-full rounded-full">
                    <div className="progress-fill h-full rounded-full" style={{ width: `${item.progress * 100}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Scanlines, vignette, one soft reflection. */}
          <div className="pointer-events-none absolute inset-0 tv-glare opacity-60" aria-hidden />
        </div>
      </div>

      {/* The phone remote, leaning against the set. Hidden on small screens. */}
      <motion.div
        className="absolute -right-2 bottom-4 hidden w-[86px] rounded-[18px] border border-white/12 bg-[#0d0f14] p-2 shadow-lift lg:block"
        initial={reduce ? false : { opacity: 0, y: 16, rotate: 6 }}
        animate={{ opacity: 1, y: 0, rotate: 4 }}
        transition={{ delay: 0.6, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="mx-auto mb-1.5 h-1 w-6 rounded-full bg-white/20" />
        <div className="relative grid aspect-square place-items-center rounded-full border border-white/10">
          <div className="grid h-7 w-7 place-items-center rounded-full bg-white/90 text-[9px] font-bold text-black">
            OK
          </div>
          <span className="absolute top-1 text-[7px] text-white/40">▲</span>
          <span className="absolute bottom-1 text-[7px] text-white/40">▼</span>
          <span className="absolute left-1 text-[7px] text-white/40">◀</span>
          <span className="absolute right-1 text-[7px] text-white/40">▶</span>
        </div>
        <div className="mt-1.5 flex justify-center gap-1">
          <span className="h-1.5 w-4 rounded-full bg-white/15" />
          <span className="h-1.5 w-4 rounded-full bg-phosphor/60" />
        </div>
        <p className="mt-1.5 text-center text-[6px] uppercase tracking-[0.16em] text-white/35">
          Couch Remote
        </p>
      </motion.div>
    </div>
  );
}
