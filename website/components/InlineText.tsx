import type { ReactNode } from 'react';

/**
 * Renders a string that may contain `inline code` in backticks.
 *
 * Copy lives in `lib/site.ts` as plain strings so it stays readable and diffable; this turns the
 * backticked spans into `<code>` chips without pulling in a Markdown dependency. If the number of
 * backticks is odd the text is rendered untouched, so a stray backtick can never swallow a
 * paragraph.
 */
export default function InlineText({ text }: { text: string }): ReactNode {
  const parts = text.split('`');
  if (parts.length % 2 === 0) return text;

  return parts.map((part, index) =>
    index % 2 === 1 ? (
      <code key={index} className="inline-code">
        {part}
      </code>
    ) : (
      part
    )
  );
}
