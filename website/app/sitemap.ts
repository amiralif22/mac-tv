import type { MetadataRoute } from 'next';
import { site } from '@/lib/site';

/** Four pages, all static. The date is the build date, which is the only truthful one. */
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  return [
    { url: `${site.url}/`, lastModified: now, changeFrequency: 'monthly', priority: 1 },
    { url: `${site.url}/features`, lastModified: now, changeFrequency: 'monthly', priority: 0.8 },
    { url: `${site.url}/download`, lastModified: now, changeFrequency: 'weekly', priority: 0.9 },
    { url: `${site.url}/docs`, lastModified: now, changeFrequency: 'monthly', priority: 0.7 },
  ];
}
