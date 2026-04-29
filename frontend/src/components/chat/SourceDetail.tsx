import { Drawer, Tag, Typography } from 'antd';
import { FileTextOutlined, LinkOutlined, NumberOutlined } from '@ant-design/icons';
import type { CitationData } from './CitationBadge';
import styles from './SourceDetail.module.css';

const { Text } = Typography;

interface SourceDetailProps {
  open: boolean;
  citation: CitationData | null;
  onClose: () => void;
}

const sourceColorMap: Record<string, string> = {
  RAG: 'green',
  FALLBACK: 'orange',
  ERROR: 'red',
};

const sourceLabelMap: Record<string, string> = {
  RAG: 'RAG 检索',
  FALLBACK: '直连模式',
  ERROR: '错误',
};

export default function SourceDetail({ open, citation, onClose }: SourceDetailProps) {
  if (!citation) return null;

  return (
    <Drawer
      title={
        <span>
          <FileTextOutlined style={{ marginRight: 8 }} />
          参考来源详情
        </span>
      }
      open={open}
      onClose={onClose}
      width={480}
      destroyOnClose
    >
      {/* Metadata Grid */}
      <div className={styles.detailMeta}>
        <div className={styles.metaItem}>
          <div className={styles.metaItemLabel}>来源类型</div>
          <div className={styles.metaItemValue}>
            <Tag color={sourceColorMap[citation.source] || 'default'}>
              {sourceLabelMap[citation.source] || citation.source}
            </Tag>
          </div>
        </div>
        <div className={styles.metaItem}>
          <div className={styles.metaItemLabel}>检索片段数</div>
          <div className={styles.metaItemValue}>{citation.chunkCount}</div>
        </div>
        {citation.score != null && (
          <div className={styles.metaItem}>
            <div className={styles.metaItemLabel}>相关度评分</div>
            <div className={styles.metaItemValue}>
              {(citation.score * 100).toFixed(1)}%
            </div>
          </div>
        )}
        {citation.documentName && (
          <div className={styles.metaItem}>
            <div className={styles.metaItemLabel}>文档名称</div>
            <div className={styles.metaItemValue} style={{ fontSize: 14 }}>
              {citation.documentName}
            </div>
          </div>
        )}
      </div>

      {/* Source Info */}
      <div className={styles.detailSection}>
        <div className={styles.detailLabel}>
          <LinkOutlined /> 来源标识
        </div>
        <div className={styles.detailValue}>
          <Text copyable>{citation.source}</Text>
        </div>
      </div>

      {/* Chunk Count Detail */}
      <div className={styles.detailSection}>
        <div className={styles.detailLabel}>
          <NumberOutlined /> 检索片段
        </div>
        <div className={styles.detailValue}>
          共检索到 <Text strong>{citation.chunkCount}</Text> 个与问题相关的文本片段
        </div>
      </div>

      {/* Context Preview */}
      {citation.contextPreview && (
        <div className={styles.detailSection}>
          <div className={styles.detailLabel}>
            <FileTextOutlined /> 上下文预览
          </div>
          <div className={styles.contextBlock}>
            {citation.contextPreview}
          </div>
        </div>
      )}
    </Drawer>
  );
}
