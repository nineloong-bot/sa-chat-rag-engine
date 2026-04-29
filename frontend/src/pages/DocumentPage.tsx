import { Space, message, theme } from 'antd';
import DocumentUpload from '@/components/document/DocumentUpload';
import TaskProgress from '@/components/document/TaskProgress';
import { useDocumentStore } from '@/stores/documentStore';

export default function DocumentPage() {
  const { tasks, uploadDocument, uploading } = useDocumentStore();
  const { token } = theme.useToken();

  const handleUpload = async (file: File) => {
    message.loading({ content: `正在上传 ${file.name}...`, key: 'upload' });
    await uploadDocument(file);
    message.success({ content: `${file.name} 上传成功，开始处理`, key: 'upload' });
  };

  return (
    <div style={{
      padding: 24,
      maxWidth: 900,
      margin: '0 auto',
      height: '100%',
      overflow: 'auto',
    }}>
      <div style={{ marginBottom: 24 }}>
        <DocumentUpload onUpload={handleUpload} disabled={uploading} />
      </div>

      {tasks.length > 0 && (
        <div>
          <h3 style={{ fontSize: 16, marginBottom: 12 }}>处理任务</h3>
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            {tasks.map((task) => (
              <TaskProgress key={task.taskId} task={task} />
            ))}
          </Space>
        </div>
      )}
    </div>
  );
}
