import { Card, Tag, Typography, theme } from 'antd';
import { UserOutlined, RobotOutlined, LoadingOutlined } from '@ant-design/icons';
import type { Message } from '@/types';
import SourceCard from './SourceCard';

const { Paragraph } = Typography;

interface ChatBubbleProps {
  message: Message;
  streaming?: boolean;
}

export default function ChatBubble({ message, streaming }: ChatBubbleProps) {
  const { token } = theme.useToken();
  const isUser = message.role === 'user';

  return (
    <div style={{
      display: 'flex',
      justifyContent: isUser ? 'flex-end' : 'flex-start',
      marginBottom: 16,
      padding: '0 12px',
    }}>
      <div style={{
        maxWidth: '75%',
        display: 'flex',
        gap: 10,
        flexDirection: isUser ? 'row-reverse' : 'row',
      }}>
        {/* Avatar */}
        <div style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          background: isUser ? token.colorPrimary : token.colorSuccess,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontSize: 16,
          flexShrink: 0,
          marginTop: 4,
        }}>
          {isUser ? <UserOutlined /> : <RobotOutlined />}
        </div>

        {/* Content */}
        <div>
          <Card
            size="small"
            style={{
              background: isUser ? token.colorPrimaryBg : token.colorBgContainer,
              borderColor: isUser ? token.colorPrimaryBorder : token.colorBorderSecondary,
            }}
            styles={{ body: { padding: '12px 16px' } }}
          >
            <Paragraph style={{
              margin: 0,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}>
              {message.content || (streaming ? <LoadingOutlined /> : '')}
              {streaming && <span style={{
                display: 'inline-block',
                width: 2,
                height: '1em',
                background: token.colorPrimary,
                marginLeft: 2,
                animation: 'blink 1s step-end infinite',
              }} />}
            </Paragraph>
          </Card>

          {/* Source info */}
          {!isUser && message.source && (
            <SourceCard
              source={message.source}
              relevantChunkCount={message.relevantChunkCount || 0}
              contextPreview={message.contextPreview}
            />
          )}
        </div>
      </div>
    </div>
  );
}
