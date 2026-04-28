import { useEffect, useState } from 'react';
import { Typography } from 'antd';

const { Paragraph } = Typography;

interface StreamingTextProps {
  text: string;
}

export default function StreamingText({ text }: StreamingTextProps) {
  const [cursorVisible, setCursorVisible] = useState(true);

  useEffect(() => {
    const timer = setInterval(() => {
      setCursorVisible((v) => !v);
    }, 530);
    return () => clearInterval(timer);
  }, []);

  return (
    <span>
      <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word', display: 'inline' }}>
        {text}
      </Paragraph>
      <span style={{
        display: 'inline-block',
        width: 2,
        height: '1em',
        background: cursorVisible ? '#1677ff' : 'transparent',
        marginLeft: 2,
        verticalAlign: 'text-bottom',
        transition: 'background 0.1s',
      }} />
    </span>
  );
}
