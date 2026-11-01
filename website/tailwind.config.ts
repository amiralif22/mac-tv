import type { Config } from 'tailwindcss';

/**
 * The palette is the Mac TV icon: cream, charcoal and brass, with the CRT's phosphor green.
 * Dark is the default — the launcher is a dark-room product — and light mode borrows the cream
 * of the original site so both halves of the project feel like one family.
 */
const config: Config = {
  darkMode: 'class',
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}', './lib/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: 'rgb(var(--canvas) / <alpha-value>)',
        panel: 'rgb(var(--panel) / <alpha-value>)',
        raised: 'rgb(var(--raised) / <alpha-value>)',
        ink: 'rgb(var(--ink) / <alpha-value>)',
        dim: 'rgb(var(--dim) / <alpha-value>)',
        faint: 'rgb(var(--faint) / <alpha-value>)',
        line: 'rgb(var(--line) / <alpha-value>)',
        phosphor: 'rgb(var(--phosphor) / <alpha-value>)',
        brass: 'rgb(var(--brass) / <alpha-value>)',
      },
      fontFamily: {
        display: ['var(--font-display)'],
        mono: ['var(--font-mono)'],
        sans: ['var(--font-body)'],
      },
      maxWidth: { wrap: '1160px' },
      borderRadius: { tv: '1.25rem', panel: '1.75rem' },
      boxShadow: {
        lift: '0 30px 60px -30px rgb(0 0 0 / 0.55)',
        glow: '0 0 0 1px rgb(var(--phosphor) / 0.22), 0 24px 60px -24px rgb(var(--phosphor) / 0.22)',
      },
      backgroundImage: {
        ambient:
          'radial-gradient(70vmax 60vmax at 8% -10%, rgb(var(--phosphor) / 0.10), transparent 70%), radial-gradient(60vmax 55vmax at 96% 12%, rgb(70 110 255 / 0.12), transparent 70%)',
      },
      keyframes: {
        drift: {
          '0%, 100%': { transform: 'translate3d(0,0,0) scale(1)' },
          '50%': { transform: 'translate3d(1.5%, -1.5%, 0) scale(1.05)' },
        },
      },
      animation: { drift: 'drift 26s ease-in-out infinite' },
    },
  },
  plugins: [],
};

export default config;
