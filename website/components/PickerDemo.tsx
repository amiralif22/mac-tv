'use client';

import { useMemo, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';

/**
 * The first-launch picker, as an interactive demo. It is not a screenshot of the app: it is the
 * same behaviour (nothing ticked by default, categories, select all) rendered small, and the row
 * below is what the home screen would actually draw from those choices.
 */

type App = { id: string; name: string; category: string; hue: number; size: 'tv' | 'phone' };

const catalogue: App[] = [
  { id: 'nuvio', name: 'Nuvio', category: 'Streaming', hue: 152, size: 'tv' },
  { id: 'netflix', name: 'Netflix', category: 'Streaming', hue: 355, size: 'tv' },
  { id: 'prime', name: 'Prime Video', category: 'Streaming', hue: 205, size: 'tv' },
  { id: 'youtube', name: 'YouTube', category: 'Streaming', hue: 0, size: 'phone' },
  { id: 'spotify', name: 'Spotify', category: 'Music', hue: 122, size: 'tv' },
  { id: 'photos', name: 'Photos', category: 'Media', hue: 42, size: 'tv' },
  { id: 'plex', name: 'Plex', category: 'Streaming', hue: 42, size: 'tv' },
  { id: 'steam', name: 'Steam Link', category: 'Games', hue: 210, size: 'phone' },
];

const categories = ['Streaming', 'Music', 'Media', 'Games'] as const;

export default function PickerDemo() {
  const [chosen, setChosen] = useState<string[]>(['nuvio', 'netflix', 'spotify']);
  const reduce = useReducedMotion();

  const allChosen = chosen.length === catalogue.length;

  const toggle = (id: string) =>
    setChosen((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );

  const selected = useMemo(
    () => catalogue.filter((app) => chosen.includes(app.id)),
    [chosen],
  );

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.92fr)] lg:gap-8">
      {/* The picker panel */}
      <div className="rounded-panel border border-line/12 bg-panel/70 p-4 sm:p-5">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div>
            <p className="label text-faint">First launch</p>
            <p className="mt-1 text-sm font-semibold text-ink">Choose your apps</p>
          </div>
          <button
            type="button"
            onClick={() => setChosen(allChosen ? [] : catalogue.map((app) => app.id))}
            className="rounded-full border border-line/15 px-3 py-1.5 text-xs font-medium text-dim transition-colors hover:border-phosphor/50 hover:text-ink"
          >
            {allChosen ? 'Deselect all' : 'Select all'}
          </button>
        </div>

        <div className="space-y-4">
          {categories.map((category) => {
            const apps = catalogue.filter((app) => app.category === category);
            if (!apps.length) return null;
            return (
              <div key={category}>
                <p className="label mb-2 text-faint">{category}</p>
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  {apps.map((app) => {
                    const on = chosen.includes(app.id);
                    return (
                      <button
                        key={app.id}
                        type="button"
                        aria-pressed={on}
                        onClick={() => toggle(app.id)}
                        className={`group relative flex items-center gap-2 rounded-xl border p-2 text-left transition-all ${
                          on
                            ? 'border-phosphor/50 bg-phosphor/10'
                            : 'border-line/12 bg-raised/40 hover:border-line/25'
                        }`}
                      >
                        <span
                          className="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-[9px] font-bold text-white/90"
                          style={{
                            background: `linear-gradient(150deg, hsl(${app.hue} 48% 34%), hsl(${app.hue} 58% 15%))`,
                          }}
                        >
                          {app.name.slice(0, 2)}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-xs font-medium text-ink">{app.name}</span>
                          <span className="block text-[10px] text-faint">
                            {app.size === 'tv' ? 'Remote ready' : 'Touch-first'}
                          </span>
                        </span>
                        <span
                          className={`grid h-4 w-4 shrink-0 place-items-center rounded-[5px] border transition-colors ${
                            on ? 'border-phosphor bg-phosphor text-black' : 'border-line/25 text-transparent'
                          }`}
                          aria-hidden
                        >
                          <svg width="10" height="10" viewBox="0 0 12 12" fill="none">
                            <path
                              d="m2.6 6.3 2.2 2.2L9.5 3.8"
                              stroke="currentColor"
                              strokeWidth="2"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                            />
                          </svg>
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>

        <div className="mt-4 flex items-center justify-between gap-3 border-t border-line/10 pt-4">
          <p className="text-[11px] text-faint">
            {chosen.length} of {catalogue.length} chosen · nothing is ticked until you tick it
          </p>
          <span className="rounded-full bg-ink px-3.5 py-1.5 text-xs font-semibold text-canvas">
            Set up my home screen
          </span>
        </div>
      </div>

      {/* What the home screen would show */}
      <div className="rounded-panel border border-line/12 bg-panel/50 p-4 sm:p-5">
        <div className="mb-3 flex items-center justify-between">
          <p className="label text-faint">Your home screen</p>
          <span className="font-mono text-[10px] text-faint">live preview</span>
        </div>

        <div className="relative overflow-hidden rounded-2xl border border-line/10 bg-[#07080b] p-4">
          <div
            className="pointer-events-none absolute inset-0 opacity-70"
            style={{
              backgroundImage:
                'radial-gradient(90% 70% at 15% 0%, hsl(152 50% 24% / 0.45), transparent 65%), radial-gradient(80% 60% at 90% 20%, hsl(210 60% 24% / 0.4), transparent 60%)',
            }}
          />
          <div className="relative">
            <div className="flex items-center justify-between text-[9px] uppercase tracking-[0.18em] text-white/40">
              <span>Couch TV</span>
              <span className="font-mono normal-case tracking-normal text-white/60">21:14</span>
            </div>

            <p className="mt-4 text-[9px] font-semibold uppercase tracking-[0.2em] text-phosphor">
              Your apps
            </p>

            <div className="mt-2 min-h-[74px]">
              <AnimatePresence mode="popLayout">
                {selected.length ? (
                  <motion.div layout className="flex flex-wrap gap-2">
                    {selected.map((app) => (
                      <motion.div
                        key={app.id}
                        layout={!reduce}
                        initial={reduce ? false : { opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={reduce ? undefined : { opacity: 0, scale: 0.9 }}
                        transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
                        className="w-[64px]"
                      >
                        <div
                          className="relative overflow-hidden rounded-lg"
                          style={{
                            aspectRatio: '5 / 3',
                            background: `linear-gradient(150deg, hsl(${app.hue} 44% 32%), hsl(${app.hue} 58% 15%))`,
                            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.08)',
                          }}
                        >
                          <div
                            className="absolute inset-0 opacity-70"
                            style={{
                              backgroundImage: `radial-gradient(60% 60% at 30% 25%, hsl(${app.hue} 70% 62% / 0.5), transparent 70%)`,
                            }}
                          />
                        </div>
                        <p className="mt-1 truncate text-[7px] text-white/50">{app.name}</p>
                      </motion.div>
                    ))}
                  </motion.div>
                ) : (
                  <motion.p
                    key="empty"
                    initial={reduce ? false : { opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="text-[10px] leading-relaxed text-white/40"
                  >
                    Nothing yet. The launcher draws only what you ticked — this is the empty state a
                    fresh install really has.
                  </motion.p>
                )}
              </AnimatePresence>
            </div>

            <p className="mt-4 text-[9px] font-semibold uppercase tracking-[0.2em] text-white/35">
              Continue watching
            </p>
            <div className="mt-2 flex gap-2 opacity-45">
              {[18, 205, 268].map((hue) => (
                <div key={hue} className="w-9">
                  <div
                    className="rounded-[4px]"
                    style={{
                      aspectRatio: '2 / 3',
                      background: `linear-gradient(160deg, hsl(${hue} 40% 28%), hsl(${hue} 55% 11%))`,
                    }}
                  />
                  <div className="mt-1 h-[2px] w-full rounded-full bg-white/15" />
                </div>
              ))}
            </div>
          </div>
        </div>

        <p className="mt-3 text-xs leading-relaxed text-dim">
          Rows collapse when they have nothing to show. With no streaming account connected, the
          screen is your apps and nothing else — no promotional rows, ever.
        </p>
      </div>
    </div>
  );
}
