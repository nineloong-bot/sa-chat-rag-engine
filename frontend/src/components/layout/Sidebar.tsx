import { Menu } from 'antd';
import { MessageOutlined, FileTextOutlined, HistoryOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useChatStore } from '@/stores/chatStore';

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const newSession = useChatStore((s) => s.newSession);
  const sessionId = useChatStore((s) => s.sessionId);

  // Match /chat/:sessionId as the "对话" route
  const isChatRoute = location.pathname === '/' || location.pathname.startsWith('/chat/');

  const items = [
    {
      key: 'new',
      icon: <PlusCircleOutlined />,
      label: '新建对话',
      onClick: () => {
        newSession();
        // Read the new sessionId from store after newSession
        const newId = useChatStore.getState().sessionId;
        navigate(`/chat/${newId}`);
      },
    },
    { type: 'divider' as const },
    {
      key: 'chat',
      icon: <MessageOutlined />,
      label: '对话',
      onClick: () => navigate(`/chat/${sessionId}`),
    },
    {
      key: '/documents',
      icon: <FileTextOutlined />,
      label: '文档管理',
      onClick: () => navigate('/documents'),
    },
    {
      key: '/history',
      icon: <HistoryOutlined />,
      label: '历史记录',
      onClick: () => navigate('/history'),
    },
  ];

  const selectedKey = isChatRoute ? 'chat' : location.pathname;

  return (
    <div style={{ padding: '16px 0' }}>
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        items={items}
        style={{ border: 'none' }}
      />
    </div>
  );
}
