# The Couch TV website

The site for the ecosystem: the launcher, the two remotes, and the Mac app they grew out of. Next.js 14
(App Router), TypeScript, Tailwind, Framer Motion. Four routes, all prerendered.

```bash
npm ci
npm run dev        # http://localhost:3000
npm run build      # static output in .next
npm run lint       # next lint
npm run typecheck  # tsc --noEmit
```

## Pages

| Route | What it is |
|---|---|
| `/` | Hero with a TV drawn in CSS, the interactive app-picker demo, features, remote section, comparison table |
| `/features` | Every capability, the measured numbers, and what the app deliberately does not do |
| `/download` | Four downloads with QR codes, four setup steps, build and verify instructions |
| `/docs` | Setup, sideloading, streaming, pairing, writing an adapter, troubleshooting, FAQ |

## How it is built

- **No images.** The TV, the app tiles, the posters and the picker are DOM and CSS, so the page is sharp
  on any display, costs no image requests, and cannot drift out of date the way a screenshot can. The
  only binary asset is the favicon; the social card is generated at build time by `app/opengraph-image.tsx`.
- **No client-side data.** Every route is a server component rendering static HTML; Framer Motion and
  the demo widgets are the only client components, and they are small.
- **No analytics, no cookies, no fonts to fetch.** System fonts with a monospace for labels, matching
  the Mac app's own typography.
- **Colour is one vocabulary.** `--canvas`, `--ink`, `--phosphor` and friends in `app/globals.css`, with
  dark as the default and a cream light mode that echoes the original Mac TV site.
- **Accessibility.** Skip link, focus-visible rings, `aria-current` on navigation, reduced-motion
  honoured in CSS and in Framer, real `<table>` semantics for the comparison, and every control is a
  button or a link with a name.

## Deploying to Vercel

The GitHub repository is a monorepo, so the Vercel project's **Root Directory must be `website`**.
Everything else is automatic:

1. Import the repository at vercel.com.
2. Set Root Directory to `website`; Framework Preset is detected as Next.js.
3. Deploy. Every push to `main` then publishes, and pull requests get preview URLs.

Alternatively, from this folder:

```bash
npx vercel        # first run links the project
npx vercel --prod
```

`vercel.json` pins the framework, the install command (`npm ci`) and a few security headers. There are
no environment variables to set; `NEXT_PUBLIC_SITE_URL` is optional and only changes canonical URLs and
the sitemap.

## Checks

CI runs `npm ci`, `typecheck`, `lint`, `build`, and then asserts that `index.html`, `features.html`,
`download.html` and `docs.html` all exist in `.next/server/app` — the site must stay fully static.
