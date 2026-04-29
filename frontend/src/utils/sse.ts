import type { StreamEvent } from '@/types';

/**
 * SSE 流式事件处理器。
 */
export interface SSEHandlers {
  onChunk?: (content: string) => void;
  onSource?: (source: string, chunkCount: number, contextPreview: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
}

/**
 * 解析 SSE 流式响应，统一处理 data: 行的 JSON 解析和事件分发。
 *
 * @param reader ReadableStream 的 reader
 * @param handlers 事件回调
 */
export async function consumeSSEStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  handlers: SSEHandlers,
): Promise<void> {
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const jsonStr = line.slice(5).trimStart();
      if (!jsonStr) continue;

      try {
        const event: StreamEvent = JSON.parse(jsonStr);
        switch (event.type) {
          case 'chunk':
            handlers.onChunk?.(event.content ?? '');
            break;
          case 'source':
            handlers.onSource?.(
              event.source ?? '',
              event.relevantChunkCount ?? 0,
              event.contextPreview ?? '',
            );
            break;
          case 'done':
            handlers.onDone?.();
            break;
          case 'error':
            handlers.onError?.(event.message ?? '未知错误');
            break;
        }
      } catch {
        // Skip unparseable lines
      }
    }
  }
}
