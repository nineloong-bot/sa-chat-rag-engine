import { useState, memo } from 'react';
import { Tag } from 'antd';
import { LinkOutlined, DownOutlined, UpOutlined, ExpandOutlined } from '@ant-design/icons';
import type { CitationData } from './CitationBadge';
import styles from './SourceCard.module.css';

interface SourceCardProps {
  source: string;
  relevantChunkCount: number;
  contextPreview?: string;
  onViewDetail?: (citation: CitationData) => void;
}

const colorMap: Record<string, string> = {
  RAG: 'green',
  FALLBACK: 'orange',
  ERROR: 'red',
};

const labelMap: Record<string, string> = {
  RAG: 'RAG 检索',
  FALLBACK: '直连模式',
  ERROR: '错误',
};

function SourceCard({ source, relevantChunkCount, contextPreview, onViewDetail }: SourceCardProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className={styles.sourceCard}>
      <div className={styles.sourceHeader}>
        <LinkOutlined style={{ color: '#1677ff' }} />
        <Tag color={colorMap[source] || 'default'}>
          {labelMap[source] || source}
        </Tag>
        {source === 'RAG' && (
          <span style={{ fontSize: 12, color: '#666' }}>
            检索到 {relevantChunkCount} 个相关片段
          </span>
        )}
        {contextPreview && (
          <button
            className={styles.expandBtn}
            onClick={() => setExpanded(!expanded)}
            style={{ marginLeft: 'auto' }}
          >
            {expanded ? <><UpOutlined /> 收起</> : <><DownOutlined /> 展开</>}
          </button>
        )}
        {onViewDetail && (
          <button
            className={styles.expandBtn}
            onClick={() => onViewDetail({
              index: 1,
              source,
              chunkCount: relevantChunkCount,
              contextPreview: contextPreview || '',
            })}
          >
            <ExpandOutlined /> 详情
          </button>
        )}
      </div>
      {contextPreview && (
        <div className={`${styles.sourcePreview} ${expanded ? styles['sourcePreview--expanded'] : ''}`}>
          {contextPreview}
        </div>
      )}
    </div>
  );
}

export default memo(SourceCard);
