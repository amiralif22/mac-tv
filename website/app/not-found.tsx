import Link from 'next/link';

export default function NotFound() {
  return (
    <section className="mx-auto flex max-w-wrap flex-col items-center px-4 py-32 text-center sm:px-6">
      <p className="label text-phosphor">404 · no signal</p>
      <h1 className="display mt-4 text-[clamp(2rem,5vw,3.4rem)]">This channel is off air.</h1>
      <p className="mt-4 max-w-md text-base text-dim">
        The page you asked for does not exist. The home screen does, which is rather the point.
      </p>
      <Link href="/" className="mt-7 rounded-full bg-ink px-6 py-3 text-sm font-semibold text-canvas">
        Back to the home screen
      </Link>
    </section>
  );
}
