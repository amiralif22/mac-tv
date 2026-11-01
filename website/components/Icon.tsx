/**
 * The feature glyphs, hand-drawn to one 24-unit grid and one stroke weight so they belong together.
 */
export default function Icon({ name, className }: { name: string; className?: string }) {
  const common = {
    width: 22,
    height: 22,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.7,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
    className,
  };

  switch (name) {
    case 'screens':
      return (
        <svg {...common}>
          <rect x="2.5" y="5" width="13" height="9" rx="1.8" />
          <rect x="12.5" y="10" width="9" height="9" rx="1.6" />
          <path d="M7 17v2.5M2.5 19.5h8" />
        </svg>
      );
    case 'check':
      return (
        <svg {...common}>
          <rect x="3" y="3" width="8" height="8" rx="2" />
          <rect x="13" y="13" width="8" height="8" rx="2" />
          <path d="m5.4 7 1.6 1.7L9.5 5.6M15.4 17l1.6 1.7 2.5-3.1" />
        </svg>
      );
    case 'download':
      return (
        <svg {...common}>
          <path d="M12 3v11m0 0 3.8-3.9M12 14l-3.8-3.9" />
          <path d="M4 16.5v2.2A2.3 2.3 0 0 0 6.3 21h11.4a2.3 2.3 0 0 0 2.3-2.3v-2.2" />
        </svg>
      );
    case 'remote':
      return (
        <svg {...common}>
          <rect x="7.5" y="2.5" width="9" height="19" rx="4.2" />
          <circle cx="12" cy="9" r="2.6" />
          <path d="M12 15.5h.01M14.5 18h.01M9.5 18h.01" />
        </svg>
      );
    case 'play':
      return (
        <svg {...common}>
          <rect x="2.5" y="4.5" width="19" height="13" rx="2" />
          <path d="m10 8.4 5 2.9-5 2.9z" fill="currentColor" stroke="none" />
          <path d="M8.5 21h7" />
        </svg>
      );
    case 'pointer':
      return (
        <svg {...common}>
          <path d="m6 3 12 7.4-5.2 1.3L10 18z" />
          <path d="M14 15.5 18 21" />
        </svg>
      );
    case 'dpad':
      return (
        <svg {...common}>
          <path d="M12 3.5 14.6 6H9.4L12 3.5ZM12 20.5 9.4 18h5.2L12 20.5Z" />
          <path d="M3.5 12 6 9.4v5.2L3.5 12ZM20.5 12 18 14.6V9.4L20.5 12Z" />
          <circle cx="12" cy="12" r="2.2" />
        </svg>
      );
    case 'bolt':
      return (
        <svg {...common}>
          <path d="M13.4 2.5 4.8 13.2h5.1L9.9 21.5l8.7-10.7h-5.2z" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="8.5" />
        </svg>
      );
  }
}
