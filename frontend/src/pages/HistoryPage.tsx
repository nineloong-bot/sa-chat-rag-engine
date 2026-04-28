import { useEffect, useState } from 'react';
import { List, Typography, Tag, Empty, Button, theme } from 'antd';
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { ChatHistory } from '@/types';
import { listChatHistoryBySession, deleteChatHistory } from '@/api/chatHistory';
import { formatDate } from '@/utils/format';

const { Text, Paragraph } = Typography;

interface SessionGroup {
  sessionId: string;
  messages: ChatHistory[];
  lastUpdated: string;
}

export default function HistoryPage() {
  const [sessions, setSessions] = useState<SessionGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const loadSessions = async () => {
    setLoading(true);
    try {
      // In a real app, you'd have an API to list unique sessions.
      // For now we fetch all sessions from a known list.
      // This is a simplified approach; users would need a session-list API.
      const res = await listChatHistoryBySession('_all_');
      // Group by sessionId
      const groups = new Map<string, ChatHistory[]>();
      for (const item of res) {
        const list = groups.get(item.sessionId) || [];
        list.push(item);
        groups.set(item.sessionId, list);
      }
      const sessionList: SessionGroup[] = Array.from(groups.entries()).map(([sessionId, messages]) => ({
        sessionId,
        messages,
        lastUpdated: messages[messages.length - 1]?.createdAt || '',
      }));
      sessionList.sort((a, b) => new Date(b.lastUpdated).getTime() - new Date(a.lastUpdated).getTime());
      setSessions(sessionList);
    } catch {
      // skip - no sessions yet or API unavailable
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const handleDelete = async (id: number) => {
    try {
      await deleteChatHistory(id);
      loadSessions();
    } catch {
      // skip
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
              }}
              onClick={() => navigate(`/chat/${session.sessionId}`)}
              extra={
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    session.messages.forEach((m) => handleDelete(m.id));
                  }}
                />
              }
            >
              <List.Item.Meta
                title={
                  <Text strong>
                    会话: {session.sessionId.slice(0, 8)}...
                    <Tag style={{ marginLeft: 8 }}>{session.messages.length} 条消息</Tag>
                  </Text>
                }
                description={
                  <>
                    <Paragraph
                      ellipsis={{ rows: 1 }}
                      style={{ margin: '4px 0', color: '#666' }}
                    >
                      {session.messages[session.messages.length - 1]?.content?.slice(0, 100)}
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
