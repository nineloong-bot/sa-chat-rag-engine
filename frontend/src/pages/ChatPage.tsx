import { useEffect, useRef } from 'react';
import { Empty, Typography, message, theme } from 'antd';
import ChatBubble from '@/components/chat/ChatBubble';
import ChatInput from '@/components/chat/ChatInput';
import { useChatStore } from '@/stores/chatStore';

const { Text } = Typography;

export default function ChatPage() {
  const { messages, streaming, ask, clearMessages, error, sessionId } = useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);
  const { token } = theme.useToken();

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

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
      <div style={{
        flex: 1,
        overflow: 'auto',
        padding: '24px 0',
        background: token.colorBgLayout,
      }}>
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
        onClear={clearMessages}
        disabled={streaming}
      />
    </div>
  );
}
