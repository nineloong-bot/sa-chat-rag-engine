import { useState, useCallback, memo } from 'react';
import { Card, theme } from 'antd';
import { UserOutlined, RobotOutlined } from '@ant-design/icons';
import type { Message } from '@/types';
import MarkdownRenderer from './MarkdownRenderer';
import SourceCard from './SourceCard';
import SourceDetail from './SourceDetail';
import type { CitationData } from './CitationBadge';
import styles from './ChatBubble.module.css';

interface ChatBubbleProps {
  message: Message;
  streaming?: boolean;
}

function ChatBubble({ message, streaming }: ChatBubbleProps) {
  const { token } = theme.useToken();
  const isUser = message.role === 'user';
  const [detailOpen, setDetailOpen] = useState(false);
  const [activeCitation, setActiveCitation] = useState<CitationData | null>(null);

  const handleViewDetail = useCallback((citation: CitationData) => {
    setActiveCitation(citation);
    setDetailOpen(true);
  }, []);

  const handleCloseDetail = useCallback(() => {
    setDetailOpen(false);
  }, []);

  // Build citations array from message metadata
  const citations: CitationData[] | undefined =
    message.source && message.relevantChunkCount
      ? [
          {
            index: 1,
            source: message.source,
            chunkCount: message.relevantChunkCount,
            contextPreview: message.contextPreview || '',
          },
        ]
      : undefined;

  return (
    <>
      <div
        className={`${styles.bubbleRow} ${isUser ? styles['bubbleRow--user'] : styles['bubbleRow--assistant']}`}
      >
        <div
          className={`${styles.bubbleInner} ${isUser ? styles['bubbleInner--user'] : styles['bubbleInner--assistant']}`}
        >
          {/* Avatar */}
          <div
            className={`${styles.avatar} ${isUser ? styles['avatar--user'] : styles['avatar--assistant']}`}
          >
            {isUser ? <UserOutlined /> : <RobotOutlined />}
          </div>

          {/* Content */}
          <div className={styles.contentWrap}>
            <Card
              size="small"
              style={{
                background: isUser ? token.colorPrimaryBg : token.colorBgContainer,
                borderColor: isUser ? token.colorPrimaryBorder : token.colorBorderSecondary,
              }}
              styles={{ body: { padding: '12px 16px' } }}
            >
              {isUser ? (
                /* User messages: plain text */
                <div style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                  {message.content}
                </div>
              ) : message.content ? (
                /* Assistant messages: markdown rendering */
                <MarkdownRenderer
                  content={message.content}
                  citations={citations}
                  onViewCitationDetail={handleViewDetail}
                />
              ) : streaming ? (
                /* Streaming with no content yet: thinking dots */
                <div className={styles.thinkingDots}>
                  <span className={styles.thinkingDot} />
                  <span className={styles.thinkingDot} />
                  <span className={styles.thinkingDot} />
                </div>
              ) : null}

              {/* Streaming cursor */}
              {streaming && message.content && (
                <span className={styles.cursor} />
              )}
            </Card>

            {/* Source info */}
            {!isUser && message.source && (
              <SourceCard
                source={message.source}
                relevantChunkCount={message.relevantChunkCount || 0}
                contextPreview={message.contextPreview}
                onViewDetail={handleViewDetail}
              />
            )}
          </div>
        </div>
      </div>

      {/* Source Detail Drawer */}
      <SourceDetail
        open={detailOpen}
        citation={activeCitation}
        onClose={handleCloseDetail}
      />
    </>
  );
}

export default memo(ChatBubble);
