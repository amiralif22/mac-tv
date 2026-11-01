import Link from 'next/link';
import { nav, site } from '@/lib/site';

export default function Footer() {
  return (
    <footer className="mt-24 border-t border-line/10">
      <div className="mx-auto flex max-w-wrap flex-col gap-8 px-4 py-12 sm:px-6 md:flex-row md:items-start md:justify-between">
        <div className="max-w-sm">
          <p className="label text-dim">{site.family}</p>
          <p className="mt-3 text-sm leading-relaxed text-dim">
            The launcher for the biggest screen in the house. Mac, Android TV, and a phone in each
            hand. Free and open source.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-10 sm:grid-cols-3">
          <div>
            <p className="label mb-3 text-faint">Site</p>
            <ul className="space-y-2 text-sm">
              {nav.map((item) => (
                <li key={item.href}>
                  <Link href={item.href} className="text-dim hover:text-ink">
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <p className="label mb-3 text-faint">Project</p>
            <ul className="space-y-2 text-sm">
              <li>
                <a href={site.repo} className="text-dim hover:text-ink">
                  GitHub
                </a>
              </li>
              <li>
                <a href={`${site.repo}/blob/main/ARCHITECTURE.md`} className="text-dim hover:text-ink">
                  Architecture
                </a>
              </li>
              <li>
                <a href={`${site.repo}/blob/main/shared/remote-protocol.md`} className="text-dim hover:text-ink">
                  Remote protocol
                </a>
              </li>
              <li>
                <a href={site.license} className="text-dim hover:text-ink">
                  MIT licence
                </a>
              </li>
            </ul>
          </div>
          <div>
            <p className="label mb-3 text-faint">Help</p>
            <ul className="space-y-2 text-sm">
              <li>
                <Link href="/docs" className="text-dim hover:text-ink">
                  Setup guide
                </Link>
              </li>
              <li>
                <Link href="/docs#troubleshooting" className="text-dim hover:text-ink">
                  Troubleshooting
                </Link>
              </li>
              <li>
                <a href={site.issues} className="text-dim hover:text-ink">
                  Report a bug
                </a>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-wrap border-t border-line/10 px-4 py-6 sm:px-6">
        <p className="text-xs leading-relaxed text-faint">
          Mac TV is a personal project, not affiliated with Apple, Google, Netflix, Amazon, JioStar or
          Spotify. App icons and service names belong to their owners. “Mac”, “Apple TV” and
          “Android TV” are trademarks of their respective companies.
        </p>
      </div>
    </footer>
  );
}
