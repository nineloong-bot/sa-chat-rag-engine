import { create } from 'zustand';
import type { Message } from '@/types';
import { askRagStream } from '@/api/rag';
import { createChatHistory } from '@/api/chatHistory';
import { generateSessionId } from '@/utils/format';

interface ChatState {
  sessionId: string;
  messages: Message[];
  streaming: boolean;
  streamingContent: string;
  error: string | null;

  newSession: () => void;
  ask: (question: string, documentId?: number) => Promise<void>;
  clearMessages: () => void;
}

function buildMessage(role: 'user' | 'assistant', content: string, extra?: Partial<Message>): Message {
  return {
    id: crypto.randomUUID(),
    role,
    content,
    timestamp: Date.now(),
    ...extra,
  };
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessionId: generateSessionId(),
  messages: [],
  streaming: false,
  streamingContent: '',
  error: null,

  newSession: () => {
    set({
      sessionId: generateSessionId(),
      messages: [],
      streaming: false,
      streamingContent: '',
      error: null,
    });
  },

  ask: async (question: string, documentId?: number) => {
    const state = get();
    if (state.streaming) return;

    const userMsg = buildMessage('user', question);
    const aiMsg = buildMessage('assistant', '');
    const sessionId = state.sessionId;

    set({
      messages: [...state.messages, userMsg, aiMsg],
      streaming: true,
      streamingContent: '',
      error: null,
    });

    // Save user message to backend
    createChatHistory({
      sessionId,
      role: 'user',
      content: question,
    }).catch(() => {/* non-critical */});

    let fullContent = '';
    let sourceInfo: { source: string; chunkCount: number; contextPreview: string } | null = null;

    try {
      await askRagStream(
        { question, documentId, topK: 5 },
        (chunk) => {
          fullContent += chunk;
          set((s) => ({
            streamingContent: fullContent,
            messages: s.messages.map((m, i) =>
              i === s.messages.length - 1 ? { ...m, content: fullContent } : m
            ),
          }));
        },
        (source, chunkCount, contextPreview) => {
          sourceInfo = { source, chunkCount, contextPreview };
        },
        () => {
          // On done: finalize message
          set((s) => {
            const finalMessages = s.messages.map((m, i) => {
              if (i === s.messages.length - 1) {
                return {
                  ...m,
                  content: fullContent,
                  source: sourceInfo?.source,
                  relevantChunkCount: sourceInfo?.chunkCount,
                  contextPreview: sourceInfo?.contextPreview,
                };
              }
              return m;
            });
            return { messages: finalMessages, streaming: false, streamingContent: '' };
          });

          // Save assistant message to backend
          createChatHistory({
            sessionId,
            role: 'assistant',
            content: fullContent,
            metadata: sourceInfo ? {
              source: sourceInfo.source,
              relevantChunkCount: sourceInfo.chunkCount,
              contextPreview: sourceInfo.contextPreview,
            } : undefined,
          }).catch(() => {/* non-critical */});
        },
        (errMsg) => {
          set((s) => ({
            streaming: false,
            streamingContent: '',
            error: errMsg,
            messages: s.messages.map((m, i) =>
              i === s.messages.length - 1 ? { ...m, content: m.content || `错误: ${errMsg}` } : m
            ),
          }));
        }
      );
    } catch (err) {
      set((s) => ({
        streaming: false,
        streamingContent: '',
        error: err instanceof Error ? err.message : '请求失败',
      }));
    }
  },

  clearMessages: () => {
    set({ messages: [], error: null, streaming: false, streamingContent: '' });
  },
}));
