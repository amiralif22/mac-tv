import type { Metadata, Viewport } from 'next';
import { ThemeProvider } from 'next-themes';
import Nav from '@/components/Nav';
import Footer from '@/components/Footer';
import { site } from '@/lib/site';
import './globals.css';

export const metadata: Metadata = {
  metadataBase: new URL(site.url),
  title: {
    default: `${site.family} · ${site.tagline}`,
    template: `%s · ${site.family}`,
  },
  description: site.description,
  applicationName: site.name,
  keywords: [
    'Android TV launcher',
    'smart TV',
    'Couch TV',
    'Mac TV',
    'phone remote',
    'APK sideloading',
    'Nuvio',
    'Jetpack Compose',
  ],
  authors: [{ name: 'Mac TV contributors', url: site.repo }],
  openGraph: {
    type: 'website',
    url: site.url,
    title: `${site.family} · ${site.tagline}`,
    description: site.description,
    siteName: site.name,
  },
  twitter: {
    card: 'summary_large_image',
    title: `${site.family} · ${site.tagline}`,
    description: site.description,
  },
  alternates: { canonical: '/' },
  robots: { index: true, follow: true },
  icons: {
    icon: [{ url: '/icon.svg', type: 'image/svg+xml' }],
    apple: [{ url: '/icon.svg' }],
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: dark)', color: '#07080b' },
    { media: '(prefers-color-scheme: light)', color: '#f6f1e7' },
  ],
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen antialiased">
        <ThemeProvider attribute="class" defaultTheme="dark" enableSystem disableTransitionOnChange>
          <a
            href="#main"
            className="label sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-phosphor focus:px-4 focus:py-2 focus:text-black"
          >
            Skip to content
          </a>
          <div className="pointer-events-none fixed inset-0 -z-10 bg-ambient" aria-hidden />
          <Nav />
          <main id="main">{children}</main>
          <Footer />
        </ThemeProvider>
        <script
          type="application/ld+json"
          // Structured data for the two apps; no tracking anywhere on this site.
          dangerouslySetInnerHTML={{
            __html: JSON.stringify({
              '@context': 'https://schema.org',
              '@type': 'SoftwareApplication',
              name: 'Couch TV',
              applicationCategory: 'MultimediaApplication',
              operatingSystem: 'Android 7.0+',
              offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
              description: site.description,
              url: site.url,
              isAccessibleForFree: true,
            }),
          }}
        />
      </body>
    </html>
  );
}
