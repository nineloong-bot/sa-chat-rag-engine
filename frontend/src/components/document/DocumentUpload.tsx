import { InboxOutlined } from '@ant-design/icons';
import { Upload, message, theme } from 'antd';
import type { UploadProps } from 'antd';

interface DocumentUploadProps {
  onUpload: (file: File) => void;
  disabled?: boolean;
}

export default function DocumentUpload({ onUpload, disabled }: DocumentUploadProps) {
  const { token } = theme.useToken();

  const props: UploadProps = {
    name: 'file',
    multiple: false,
    accept: '.pdf,.doc,.docx,.txt,.md,application/pdf,text/plain,text/markdown',
    showUploadList: false,
    beforeUpload: (file) => {
      const isValidType = [
        'application/pdf',
        'text/plain',
        'text/markdown',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/msword',
      ].includes(file.type) || file.name.match(/\.(pdf|doc|docx|txt|md)$/i);

      if (!isValidType) {
        message.error('不支持的文件格式，仅支持 PDF、Word、TXT、Markdown');
        return Upload.LIST_IGNORE;
      }
      if (file.size > 50 * 1024 * 1024) {
        message.error('文件大小不能超过 50MB');
        return Upload.LIST_IGNORE;
      }
      onUpload(file);
      return false;
    },
    disabled,
  };

  return (
    <Upload.Dragger {...props} style={{ padding: '24px 0' }}>
      <p className="ant-upload-drag-icon">
        <InboxOutlined style={{ fontSize: 48, color: token.colorPrimary }} />
      </p>
      <p className="ant-upload-text" style={{ fontSize: 16 }}>点击或拖拽文件到此区域上传</p>
      <p className="ant-upload-hint" style={{ color: '#999' }}>
        支持 PDF、Word、TXT、Markdown 格式，单文件不超过 50MB
      </p>
    </Upload.Dragger>
  );
}
