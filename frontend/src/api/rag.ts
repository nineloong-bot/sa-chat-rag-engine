import apiClient from './client';
import type { RagQueryRequest, RagResponse } from '@/types';

export async function askRag(params: RagQueryRequest): Promise<RagResponse> {
  const res = await apiClient.post<RagResponse>('/rag/ask', params);
  return res.data;
}

export async function askRagStream(
  params: RagQueryRequest,
  onChunk: (text: string) => void,
  onSource: (source: string, chunkCount: number, contextPreview: string) => void,
  onDone: () => void,
  onError: (message: string) => void,
): Promise<void> {
  const response = await fetch('/api/v1/rag/ask/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });

  if (!response.ok) {
    onError(`请求失败 (${response.status})`);
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    onError('浏览器不支持流式读取');
    return;
  }

  const decoder = new TextDecoder();
  let buffer = '';

  try {
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
                onChunk(event.content || '');
                break;
              case 'source':
                onSource(event.source || '', event.relevantChunkCount || 0, event.contextPreview || '');
                break;
              case 'done':
                onDone();
                break;
              case 'error':
                onError(event.message || '未知错误');
                break;
            }
          } catch {
            // Skip unparseable lines
          }
        }
      }
    }
  } catch (err) {
    onError(err instanceof Error ? err.message : '流式读取中断');
  }
}
