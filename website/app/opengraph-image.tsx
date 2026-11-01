import { ImageResponse } from 'next/og';
import { site } from '@/lib/site';

export const runtime = 'nodejs';
export const alt = 'Mac TV · Turn any screen into a smart TV';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

/**
 * The social card, generated at build time — no design file to keep in sync, no image to download.
 * Same composition as the hero: the set, the glow, one line of type.
 */
export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          background: 'linear-gradient(160deg, #10131b 0%, #07080b 70%)',
          padding: 72,
          color: '#f5f5f7',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#2B2A2E',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <div style={{ width: 26, height: 4, background: '#3DDC84', borderRadius: 2 }} />
          </div>
          <div style={{ fontSize: 24, letterSpacing: 6, textTransform: 'uppercase', color: '#9ea3af' }}>
            {site.family}
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div style={{ fontSize: 76, fontWeight: 800, letterSpacing: -3, lineHeight: 1.02, maxWidth: 900 }}>
            Your TV, but only the apps you chose.
          </div>
          <div style={{ fontSize: 28, color: '#9ea3af', maxWidth: 780, lineHeight: 1.45 }}>
            Couch TV — a fast, quiet home screen for Android TV, tablets and phones. Open source.
          </div>
        </div>

        <div style={{ display: 'flex', gap: 40, fontSize: 20, color: '#6e7482' }}>
          <span>60 fps</span>
          <span>·</span>
          <span>Under 500 ms</span>
          <span>·</span>
          <span>No accounts, no analytics</span>
        </div>

        <div
          style={{
            position: 'absolute',
            right: -140,
            top: 120,
            width: 620,
            height: 420,
            borderRadius: 32,
            background: 'linear-gradient(150deg, rgba(61,220,132,0.20), rgba(61,220,132,0))',
            filter: 'blur(2px)',
          }}
        />
      </div>
    ),
    size,
  );
}
