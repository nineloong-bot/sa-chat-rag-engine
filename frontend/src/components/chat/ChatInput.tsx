import { useState, useEffect } from 'react';
import { Input, Button, Space, Select, theme, message } from 'antd';
import { SendOutlined, ClearOutlined } from '@ant-design/icons';
import { getCompletedDocuments } from '@/api/document';
import type { DocumentEntity } from '@/types';
import styles from './ChatInput.module.css';

interface ChatInputProps {
  onSend: (question: string, documentId?: number) => void;
  onClear: () => void;
  disabled?: boolean;
}

export default function ChatInput({ onSend, onClear, disabled }: ChatInputProps) {
  const [question, setQuestion] = useState('');
  const [documentId, setDocumentId] = useState<number | undefined>();
  const [documents, setDocuments] = useState<DocumentEntity[]>([]);
  const { token } = theme.useToken();

  useEffect(() => {
    getCompletedDocuments()
      .then(setDocuments)
      .catch(() => {/* no docs yet */});
  }, []);

  const handleSend = () => {
    const q = question.trim();
    if (!q || disabled) return;
    onSend(q, documentId);
    setQuestion('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const docOptions = documents.map((d) => ({
    label: d.fileName.length > 30 ? d.fileName.slice(0, 28) + '...' : d.fileName,
    value: d.id,
  }));

  return (
    <div className={styles.inputArea} style={{ background: token.colorBgContainer }}>
      <div className={styles.inputInner}>
        <Space.Compact style={{ width: '100%' }}>
          <Input.TextArea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入您的问题... (Enter 发送，Shift+Enter 换行)"
            autoSize={{ minRows: 1, maxRows: 5 }}
            disabled={disabled}
            style={{ flex: 1 }}
          />
        </Space.Compact>
        <div className={styles.inputControls}>
          <Space>
            <Select
              placeholder={documents.length ? `选择文档 (${documents.length}份)` : '暂无已处理文档'}
              allowClear
              style={{ width: 220 }}
              value={documentId}
              onChange={(val) => setDocumentId(val)}
              options={docOptions}
              size="small"
              disabled={documents.length === 0}
              notFoundContent="暂无已处理完成的文档"
            />
          </Space>
          <Space>
            <Button
              icon={<ClearOutlined />}
              onClick={() => { onClear(); setQuestion(''); }}
              size="small"
            >
              清空对话
            </Button>
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={handleSend}
              disabled={disabled || !question.trim()}
            >
              发送
            </Button>
          </Space>
        </div>
      </div>
    </div>
  );
}
