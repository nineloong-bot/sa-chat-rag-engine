import { useState, useCallback, memo } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import styles from './CodeBlock.module.css';

interface CodeBlockProps {
  language?: string;
  children: string;
}

function CodeBlock({ language, children }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(children);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback for older browsers
      const textarea = document.createElement('textarea');
      textarea.value = children;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }, [children]);

  const displayLang = language || 'text';

  return (
    <div className={styles.codeBlock}>
      {/* Mac-style Chrome Bar */}
      <div className={styles.chromeBar}>
        <div className={styles.chromeDots}>
          <span className={`${styles.chromeDot} ${styles['chromeDot--red']}`} />
          <span className={`${styles.chromeDot} ${styles['chromeDot--yellow']}`} />
          <span className={`${styles.chromeDot} ${styles['chromeDot--green']}`} />
        </div>
        <span className={styles.chromeLang}>{displayLang}</span>
        <button
          className={`${styles.copyBtn} ${copied ? styles['copyBtn--copied'] : ''}`}
          onClick={handleCopy}
          title="复制代码"
        >
          {copied ? <><CheckOutlined /> 已复制</> : <><CopyOutlined /> 复制</>}
        </button>
      </div>

      {/* Code Content */}
      <div className={styles.codeContent}>
        <SyntaxHighlighter
          language={language || 'text'}
          style={oneDark}
          customStyle={{
            margin: 0,
            padding: 0,
            background: 'transparent',
            fontSize: 13,
            lineHeight: 1.6,
          }}
          wrapLongLines
        >
          {children}
        </SyntaxHighlighter>
      </div>
    </div>
  );
}

export default memo(CodeBlock);
