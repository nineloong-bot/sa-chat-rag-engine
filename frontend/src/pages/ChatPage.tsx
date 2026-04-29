import { useEffect, useRef, useCallback, useState } from 'react';
import { Empty, Typography, message, theme } from 'antd';
import { useParams, useNavigate } from 'react-router-dom';
import ChatBubble from '@/components/chat/ChatBubble';
import ChatInput from '@/components/chat/ChatInput';
import { useChatStore } from '@/stores/chatStore';

const { Text } = Typography;

export default function ChatPage() {
  const { sessionId: urlSessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const {
    messages, streaming, ask, clearMessages, error, sessionId,
    loadSession, newSession,
  } = useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const { token } = theme.useToken();
  const [isAtBottom, setIsAtBottom] = useState(true);

  // Sync sessionId between URL and store
  useEffect(() => {
    if (urlSessionId) {
      // URL has a sessionId — load it if different from current
      if (urlSessionId !== sessionId) {
        loadSession(urlSessionId);
      }
    } else {
      // URL is "/" — no sessionId in URL
      // If store already has messages, redirect to that session's URL
      if (messages.length > 0) {
        navigate(`/chat/${sessionId}`, { replace: true });
      } else {
        // No messages, no URL sessionId → create a new session and redirect
        newSession();
        const newId = useChatStore.getState().sessionId;
        navigate(`/chat/${newId}`, { replace: true });
      }
    }
  }, [urlSessionId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Smart auto-scroll: detect if user is at bottom
  const checkIfAtBottom = useCallback(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    const threshold = 120; // px from bottom to consider "at bottom"
    const atBottom = container.scrollHeight - container.scrollTop - container.clientHeight < threshold;
    setIsAtBottom(atBottom);
  }, []);

  // Scroll to bottom when new messages arrive AND user is at bottom
  useEffect(() => {
    if (isAtBottom) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isAtBottom]);

  useEffect(() => {
    if (error) {
      message.error(error);
    }
  }, [error]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
    }}>
      {/* Message Area */}
      <div
        ref={scrollContainerRef}
        onScroll={checkIfAtBottom}
        style={{
          flex: 1,
          overflow: 'auto',
          padding: '24px 0',
          background: token.colorBgLayout,
        }}
      >
        <div style={{ maxWidth: 900, margin: '0 auto' }}>
          {messages.length === 0 ? (
            <div style={{ textAlign: 'center', marginTop: 120 }}>
              <Empty description="开始一段新对话" />
              <Text type="secondary" style={{ fontSize: 13 }}>
                会话 ID: {sessionId.slice(0, 8)}...
              </Text>
            </div>
          ) : (
            messages.map((msg, idx) => (
              <ChatBubble
                key={msg.id}
                message={msg}
                streaming={streaming && idx === messages.length - 1 && msg.role === 'assistant'}
              />
            ))
          )}
          <div ref={bottomRef} />
        </div>
      </div>

      {/* Input Area */}
      <ChatInput
        onSend={ask}
        onClear={() => {
          clearMessages();
          // After clearing, redirect to the new session URL
          const newId = useChatStore.getState().sessionId;
          navigate(`/chat/${newId}`, { replace: true });
        }}
        disabled={streaming}
      />
    </div>
  );
}
