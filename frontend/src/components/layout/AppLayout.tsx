import { Layout, theme } from 'antd';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

const { Header, Content, Sider } = Layout;

export default function AppLayout() {
  const { token } = theme.useToken();

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider width={260} style={{ background: token.colorBgContainer, borderRight: `1px solid ${token.colorBorderSecondary}` }}>
        <Sidebar />
      </Sider>
      <Layout>
        <Header style={{
          background: token.colorBgContainer,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          padding: '0 24px',
          display: 'flex',
          alignItems: 'center',
          fontSize: 18,
          fontWeight: 600,
          color: token.colorTextHeading,
        }}>
          SA 智能对话助手
        </Header>
        <Content style={{ overflow: 'auto', padding: 0 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
