import { Menu } from 'antd';
import { MessageOutlined, FileTextOutlined, HistoryOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useChatStore } from '@/stores/chatStore';

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const newSession = useChatStore((s) => s.newSession);

  const items = [
    {
      key: 'new',
      icon: <PlusCircleOutlined />,
      label: '新建对话',
      onClick: () => {
        newSession();
        navigate('/');
      },
    },
    { type: 'divider' as const },
    {
      key: '/',
      icon: <MessageOutlined />,
      label: '对话',
      onClick: () => navigate('/'),
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

  const selectedKey = items.find((i) => i.key === location.pathname)?.key || '/';

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
