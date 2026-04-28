import { Progress, Tag, Typography, theme } from 'antd';
import { LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined } from '@ant-design/icons';
import type { DocumentTaskStatus } from '@/types';

const { Text } = Typography;

interface TaskProgressProps {
  task: DocumentTaskStatus;
}

const statusConfig: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
  PENDING: { color: 'default', icon: <ClockCircleOutlined />, label: '等待处理' },
  QUEUED: { color: 'processing', icon: <LoadingOutlined />, label: '已入队' },
  PROCESSING: { color: 'processing', icon: <LoadingOutlined />, label: '处理中' },
  COMPLETED: { color: 'success', icon: <CheckCircleOutlined />, label: '已完成' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, label: '失败' },
};

export default function TaskProgress({ task }: TaskProgressProps) {
  const config = statusConfig[task.status] || statusConfig.PENDING;
  const { token } = theme.useToken();

  return (
    <div style={{
      padding: '12px 16px',
      background: token.colorBgContainer,
      borderRadius: 8,
      border: `1px solid ${token.colorBorderSecondary}`,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Text strong style={{ fontSize: 14 }}>
          {task.status === 'COMPLETED' ? `文档 #${task.documentId}` : task.taskId?.slice(0, 8) + '...'}
        </Text>
        <Tag color={config.color} icon={config.icon}>{config.label}</Tag>
      </div>

      {(task.status === 'PROCESSING' || task.status === 'QUEUED') && (
        <Progress percent={task.progress || 0} size="small" status="active" />
      )}
      {task.status === 'COMPLETED' && (
        <Progress percent={100} size="small" />
      )}
      {task.status === 'FAILED' && (
        <Progress percent={task.progress || 0} size="small" status="exception" />
      )}

      {task.message && (
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 6 }}>
          {task.message}
        </Text>
      )}
      {task.errorMessage && (
        <Text type="danger" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
          错误: {task.errorMessage}
        </Text>
      )}
    </div>
  );
}
