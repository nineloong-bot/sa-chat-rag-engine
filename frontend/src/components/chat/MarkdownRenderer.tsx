import { memo, useMemo, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Components } from 'react-markdown';
import CodeBlock from './CodeBlock';
import CitationBadge, { type CitationData } from './CitationBadge';

interface MarkdownRendererProps {
  content: string;
  citations?: CitationData[];
  onViewCitationDetail?: (citation: CitationData) => void;
}

// Regex to match citation patterns like [1], [2], etc.
const CITATION_REGEX = /\[(\d+)\]/g;

/**
 * Parse text content and split into segments of plain text and citation markers.
 */
function parseCitations(text: string): Array<{ type: 'text'; value: string } | { type: 'citation'; index: number }> {
  const segments: Array<{ type: 'text'; value: string } | { type: 'citation'; index: number }> = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  CITATION_REGEX.lastIndex = 0;
  while ((match = CITATION_REGEX.exec(text)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', value: text.slice(lastIndex, match.index) });
    }
    segments.push({ type: 'citation', index: parseInt(match[1], 10) });
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < text.length) {
    segments.push({ type: 'text', value: text.slice(lastIndex) });
  }

  return segments;
}

/**
 * Render text with inline citation badges.
 */
function TextWithCitations({
  text,
  citations,
  onViewDetail,
}: {
  text: string;
  citations?: CitationData[];
  onViewDetail?: (citation: CitationData) => void;
}) {
  const segments = useMemo(() => parseCitations(text), [text]);

  // If no citations found in text, return plain text
  if (segments.length === 1 && segments[0].type === 'text') {
    return <>{text}</>;
  }

  return (
    <>
      {segments.map((seg, i) => {
        if (seg.type === 'text') {
          return <span key={i}>{seg.value}</span>;
        }
        // Find matching citation data
        const citationData = citations?.find((c) => c.index === seg.index);
        if (!citationData) {
          // No data for this citation, render as plain text
          return <span key={i}>[{seg.index}]</span>;
        }
        return (
          <CitationBadge
            key={i}
            citation={citationData}
            onViewDetail={onViewDetail}
          />
        );
      })}
    </>
  );
}

function MarkdownRenderer({ content, citations, onViewCitationDetail }: MarkdownRendererProps) {
  const handleViewDetail = useCallback(
    (citation: CitationData) => {
      onViewCitationDetail?.(citation);
    },
    [onViewCitationDetail],
  );

  const components: Components = useMemo(
    () => ({
      // Code blocks (fenced code with language)
      code({ className, children, ...props }) {
        const match = /language-(\w+)/.exec(className || '');
        const codeString = String(children).replace(/\n$/, '');

        // Inline code (no language class)
        if (!match && !codeString.includes('\n')) {
          return <code className={className} {...props}>{children}</code>;
        }

        // Block code
        return <CodeBlock language={match?.[1]}>{codeString}</CodeBlock>;
      },

      // Paragraphs - parse citations inline
      p({ children }) {
        // If children is a string, parse citations
        if (typeof children === 'string') {
          return (
            <p>
              <TextWithCitations
                text={children}
                citations={citations}
                onViewDetail={handleViewDetail}
              />
            </p>
          );
        }
        return <p>{children}</p>;
      },
    }),
    [citations, handleViewDetail],
  );

  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  );
}

export default memo(MarkdownRenderer);
