import apiClient from './client';
import { consumeSSEStream, type SSEHandlers } from '@/utils/sse';
import type { RagQueryRequest, RagResponse } from '@/types';

export async function askRag(params: RagQueryRequest): Promise<RagResponse> {
  const res = await apiClient.post<RagResponse>('/rag/ask', params);
  return res.data;
}

export async function askRagStream(
  params: RagQueryRequest,
  handlers: SSEHandlers,
): Promise<void> {
  const response = await fetch('/api/v1/rag/ask/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });

  if (!response.ok) {
    handlers.onError?.(`请求失败 (${response.status})`);
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    handlers.onError?.('浏览器不支持流式读取');
    return;
  }

  await consumeSSEStream(reader, handlers);
}
