import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AppLayout from '@/components/layout/AppLayout';
import ChatPage from '@/pages/ChatPage';

// Lazy-loaded pages (code splitting)
const DocumentPage = lazy(() => import('@/pages/DocumentPage'));
const HistoryPage = lazy(() => import('@/pages/HistoryPage'));

function PageFallback() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
      <Spin size="large" />
    </div>
  );
}

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
        },
      }}
    >
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<ChatPage />} />
            <Route path="/chat/:sessionId" element={<ChatPage />} />
            <Route
              path="/documents"
              element={
                <Suspense fallback={<PageFallback />}>
                  <DocumentPage />
                </Suspense>
              }
            />
            <Route
              path="/history"
              element={
                <Suspense fallback={<PageFallback />}>
                  <HistoryPage />
                </Suspense>
              }
            />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}
