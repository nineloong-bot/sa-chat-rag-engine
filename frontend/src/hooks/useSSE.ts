import { useState, useRef, useCallback } from 'react';

type SSEEventHandler = {
  onChunk?: (content: string) => void;
  onSource?: (source: string, chunkCount: number, contextPreview: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
};

export function useSSE() {
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const connect = useCallback(async (url: string, body: unknown, handlers: SSEEventHandler) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setConnecting(true);
    setError(null);

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      if (!response.ok) {
        handlers.onError?.(`请求失败 (${response.status})`);
        setError(`请求失败 (${response.status})`);
        setConnecting(false);
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        handlers.onError?.('浏览器不支持流式读取');
        setError('浏览器不支持流式读取');
        setConnecting(false);
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const jsonStr = line.slice(5).trimStart();
            if (!jsonStr) continue;
            try {
              const event = JSON.parse(jsonStr);
              switch (event.type) {
                case 'chunk':
                  handlers.onChunk?.(event.content || '');
                  break;
                case 'source':
                  handlers.onSource?.(event.source || '', event.relevantChunkCount || 0, event.contextPreview || '');
                  break;
                case 'done':
                  handlers.onDone?.();
                  break;
                case 'error':
                  handlers.onError?.(event.message || '未知错误');
                  setError(event.message || '未知错误');
                  break;
              }
            } catch { /* skip unparseable */ }
          }
        }
      }
    } catch (err) {
      if ((err as Error).name === 'AbortError') return;
      const msg = err instanceof Error ? err.message : '流式读取中断';
      handlers.onError?.(msg);
      setError(msg);
    } finally {
      setConnecting(false);
    }
  }, []);

  const abort = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { connect, abort, connecting, error };
}
