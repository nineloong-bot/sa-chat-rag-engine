import { Tag } from 'antd';
import { LinkOutlined } from '@ant-design/icons';

interface SourceCardProps {
  source: string;
  relevantChunkCount: number;
  contextPreview?: string;
}

export default function SourceCard({ source, relevantChunkCount, contextPreview }: SourceCardProps) {
  const colorMap: Record<string, string> = {
    RAG: 'green',
    FALLBACK: 'orange',
    ERROR: 'red',
  };

  return (
    <div style={{
      marginTop: 8,
      padding: '8px 12px',
      background: '#fafafa',
      borderRadius: 6,
      fontSize: 12,
      color: '#666',
    }}>
      <div style={{ marginBottom: 4 }}>
        <LinkOutlined style={{ marginRight: 4 }} />
        <Tag color={colorMap[source] || 'default'}>{source === 'RAG' ? 'RAG检索' : source === 'FALLBACK' ? '直连模式' : '错误'}</Tag>
        {source === 'RAG' && (
          <span>检索到 {relevantChunkCount} 个相关片段</span>
        )}
      </div>
      {contextPreview && (
        <div style={{
          maxHeight: 60,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          color: '#999',
        }}>
          {contextPreview}
        </div>
      )}
    </div>
  );
}
