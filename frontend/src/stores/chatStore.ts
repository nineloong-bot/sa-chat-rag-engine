import { create } from 'zustand';
import type { Message } from '@/types';
import { askRagStream } from '@/api/rag';
import { createChatHistory, listChatHistoryBySession } from '@/api/chatHistory';
import { generateSessionId } from '@/utils/format';

interface ChatState {
  sessionId: string;
  messages: Message[];
  streaming: boolean;
  error: string | null;

  newSession: () => void;
  loadSession: (sessionId: string) => Promise<void>;
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
  error: null,

  newSession: () => {
    set({
      sessionId: generateSessionId(),
      messages: [],
      streaming: false,
      error: null,
    });
  },

  loadSession: async (sessionId: string) => {
    set({ sessionId, messages: [], streaming: false, error: null });
    try {
      const history = await listChatHistoryBySession(sessionId);
      const messages: Message[] = history.map((h) => ({
        id: String(h.id),
        role: h.role as 'user' | 'assistant',
        content: h.content,
        source: h.metadata?.source as string | undefined,
        relevantChunkCount: h.metadata?.relevantChunkCount as number | undefined,
        contextPreview: h.metadata?.contextPreview as string | undefined,
        timestamp: new Date(h.createdAt).getTime(),
      }));
      set({ messages });
    } catch {
      // session not found or API unavailable
    }
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
      error: null,
    });

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
        {
          onChunk: (chunk) => {
            fullContent += chunk;
            set((s) => ({
              messages: s.messages.map((m, i) =>
                i === s.messages.length - 1 ? { ...m, content: fullContent } : m
              ),
            }));
          },
          onSource: (source, chunkCount, contextPreview) => {
            sourceInfo = { source, chunkCount, contextPreview };
          },
          onDone: () => {
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
              return { messages: finalMessages, streaming: false };
            });

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
          onError: (errMsg) => {
            set((s) => ({
              streaming: false,
              error: errMsg,
              messages: s.messages.map((m, i) =>
                i === s.messages.length - 1 ? { ...m, content: m.content || `错误: ${errMsg}` } : m
              ),
            }));
          },
        }
      );
    } catch (err) {
      set((s) => ({
        streaming: false,
        error: err instanceof Error ? err.message : '请求失败',
        messages: s.messages.map((m, i) =>
          i === s.messages.length - 1 ? { ...m, content: m.content || `错误: ${err instanceof Error ? err.message : '请求失败'}` } : m
        ),
      }));
    }
  },

  clearMessages: () => {
    const newId = generateSessionId();
    set({ messages: [], error: null, streaming: false, sessionId: newId });
  },
}));
