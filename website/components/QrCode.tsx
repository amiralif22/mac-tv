import QRCode from 'qrcode';

/**
 * A QR code, drawn at build time as inline SVG.
 *
 * Why bother: the download page is most useful on a laptop while the target is a phone or a TV in
 * the same room. The code encodes the release page, is rendered in two colours that follow the
 * theme, and is a single inline element, so it costs no request and cannot break.
 */
export default async function QrCode({
  value,
  size = 132,
  className = '',
  label,
}: {
  value: string;
  size?: number;
  className?: string;
  label?: string;
}) {
  const svg = await QRCode.toString(value, {
    type: 'svg',
    margin: 0,
    errorCorrectionLevel: 'M',
    color: { dark: '#0a0b0e', light: '#ffffff' },
  });

  return (
    <figure className={`inline-flex flex-col items-center gap-2 ${className}`}>
      <span
        className="block rounded-xl bg-white p-2 shadow-sm [&>svg]:block [&>svg]:h-full [&>svg]:w-full"
        style={{ width: size + 16, height: size + 16 }}
        // The SVG comes from the qrcode encoder, not from user input; it carries no scripts.
        dangerouslySetInnerHTML={{ __html: svg }}
        role="img"
        aria-label={label ?? `QR code linking to ${value}`}
      />
      {label && <figcaption className="text-center text-[11px] text-faint">{label}</figcaption>}
    </figure>
  );
}
