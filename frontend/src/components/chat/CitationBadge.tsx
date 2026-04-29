import { useState, memo } from 'react';
import { Popover } from 'antd';
import { FileTextOutlined, ExpandOutlined } from '@ant-design/icons';
import styles from './CitationBadge.module.css';

export interface CitationData {
  index: number;
  source: string;
  chunkCount: number;
  contextPreview: string;
  documentName?: string;
  score?: number;
}

interface CitationBadgeProps {
  citation: CitationData;
  onViewDetail?: (citation: CitationData) => void;
}

function CitationBadge({ citation, onViewDetail }: CitationBadgeProps) {
  const [open, setOpen] = useState(false);

  const popoverContent = (
    <div className={styles.popover}>
      <div className={styles.popoverTitle}>
        <FileTextOutlined />
        参考来源 [{citation.index}]
      </div>
      <div className={styles.popoverSource}>
        {citation.source === 'RAG' ? 'RAG 检索' : citation.source}
        {citation.documentName && ` · ${citation.documentName}`}
        {citation.score != null && ` · 相关度 ${(citation.score * 100).toFixed(0)}%`}
      </div>
      {citation.contextPreview && (
        <div className={styles.popoverPreview}>
          {citation.contextPreview}
        </div>
      )}
      <div
        className={styles.popoverAction}
        onClick={() => {
          setOpen(false);
          onViewDetail?.(citation);
        }}
      >
        <ExpandOutlined /> 查看完整详情
      </div>
    </div>
  );

  return (
    <Popover
      content={popoverContent}
      trigger="hover"
      open={open}
      onOpenChange={setOpen}
      placement="top"
      overlayStyle={{ maxWidth: 400 }}
    >
      <span className={styles.badge} title={`参考来源 [${citation.index}]`}>
        {citation.index}
      </span>
    </Popover>
  );
}

export default memo(CitationBadge);
