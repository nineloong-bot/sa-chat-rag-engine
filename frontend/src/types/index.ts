// ====== API Response Wrapper ======
export interface R<T = unknown> {
  code: number;
  message: string;
  data?: T;
}

// ====== RAG ======
export interface RagQueryRequest {
  question: string;
  documentId?: number;
  topK?: number;
}

export interface RagResponse {
  answer: string;
  source: 'RAG' | 'FALLBACK' | 'ERROR';
  relevantChunkCount: number;
  contextPreview: string;
}

// ====== SSE Stream Events ======
export interface StreamEvent {
  type: 'chunk' | 'source' | 'done' | 'error';
  content?: string;
  source?: string;
  relevantChunkCount?: number;
  contextPreview?: string;
  message?: string;
}

// ====== Document ======
export interface DocumentTaskStatus {
  taskId: string;
  documentId: number;
  status: string;
  progress: number;
  message: string;
  chunkCount: number;
  errorMessage?: string;
}

export interface DocumentEntity {
  id: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  filePath: string;
  status: string;
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
}

// ====== Chat History ======
export interface ChatHistory {
  id: number;
  sessionId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  model?: string;
  tokenUsage?: number;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface ChatHistoryCreateDTO {
  sessionId: string;
  role: string;
  content: string;
  model?: string;
  tokenUsage?: number;
  metadata?: Record<string, unknown>;
}

export interface ChatHistoryUpdateDTO {
  content: string;
  model?: string;
  tokenUsage?: number;
  metadata?: Record<string, unknown>;
}

// ====== Citation (RAG source reference) ======
export interface Citation {
  index: number;
  source: string;
  chunkCount: number;
  contextPreview: string;
  documentName?: string;
  score?: number;
}

// ====== Chat Message (UI) ======
export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  source?: string;
  relevantChunkCount?: number;
  contextPreview?: string;
  citations?: Citation[];
  timestamp: number;
}

// ====== Health ======
export interface HealthStatus {
  status: string;
  timestamp: string;
  application: string;
}
