import { useEffect, useState } from 'react';
import { List, Typography, Tag, Empty, Button, theme } from 'antd';
import { DeleteOutlined, ReloadOutlined, MessageOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listSessions, deleteChatHistory, listChatHistoryBySession } from '@/api/chatHistory';
import type { SessionSummary } from '@/api/chatHistory';
import { formatDate } from '@/utils/format';

const { Text, Paragraph } = Typography;

export default function HistoryPage() {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const loadSessions = async () => {
    setLoading(true);
    try {
      const data = await listSessions();
      setSessions(data);
    } catch {
      // API unavailable or no sessions
      setSessions([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const handleDelete = async (sessionId: string) => {
    try {
      // Fetch all messages in this session, then delete each
      const messages = await listChatHistoryBySession(sessionId);
      await Promise.all(messages.map((m) => deleteChatHistory(m.id)));
      loadSessions();
    } catch {
      // ignore
    }
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto', height: '100%', overflow: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h3 style={{ fontSize: 18, margin: 0 }}>历史对话</h3>
        <Button icon={<ReloadOutlined />} onClick={loadSessions} loading={loading}>刷新</Button>
      </div>

      {sessions.length === 0 && !loading ? (
        <Empty description="暂无历史对话记录" style={{ marginTop: 80 }} />
      ) : (
        <List
          loading={loading}
          dataSource={sessions}
          renderItem={(session) => (
            <List.Item
              key={session.sessionId}
              style={{
                cursor: 'pointer',
                padding: '16px',
                borderRadius: 8,
                background: token.colorBgContainer,
                marginBottom: 8,
                border: `1px solid ${token.colorBorderSecondary}`,
                transition: 'border-color 0.2s',
              }}
              onClick={() => navigate(`/chat/${session.sessionId}`)}
              extra={
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDelete(session.sessionId);
                  }}
                />
              }
            >
              <List.Item.Meta
                avatar={
                  <div style={{
                    width: 40,
                    height: 40,
                    borderRadius: '50%',
                    background: token.colorPrimaryBg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}>
                    <MessageOutlined style={{ color: token.colorPrimary }} />
                  </div>
                }
                title={
                  <Text strong>
                    {session.preview || `会话 ${session.sessionId.slice(0, 8)}...`}
                    <Tag style={{ marginLeft: 8 }}>{session.messageCount} 条消息</Tag>
                  </Text>
                }
                description={
                  <>
                    <Paragraph
                      ellipsis={{ rows: 1 }}
                      style={{ margin: '4px 0', color: '#666' }}
                    >
                      {session.preview || '暂无预览'}
                    </Paragraph>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatDate(session.lastUpdated)}
                    </Text>
                  </>
                }
              />
            </List.Item>
          )}
        />
      )}
    </div>
  );
}
