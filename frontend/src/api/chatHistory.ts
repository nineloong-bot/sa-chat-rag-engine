import apiClient from './client';
import type { ChatHistory, ChatHistoryCreateDTO, ChatHistoryUpdateDTO } from '@/types';

export interface SessionSummary {
  sessionId: string;
  messageCount: number;
  lastUpdated: string;
  preview?: string;
}

export async function createChatHistory(dto: ChatHistoryCreateDTO): Promise<ChatHistory> {
  const res = await apiClient.post<ChatHistory>('/chat-history', dto);
  return res.data;
}

export async function getChatHistoryById(id: number): Promise<ChatHistory> {
  const res = await apiClient.get<ChatHistory>(`/chat-history/${id}`);
  return res.data;
}

export async function listChatHistoryBySession(sessionId: string): Promise<ChatHistory[]> {
  const res = await apiClient.get<ChatHistory[]>(`/chat-history/session/${sessionId}`);
  return res.data;
}

export async function listSessions(): Promise<SessionSummary[]> {
  const res = await apiClient.get<SessionSummary[]>('/chat-history/sessions');
  return res.data;
}

export async function updateChatHistory(id: number, dto: ChatHistoryUpdateDTO): Promise<ChatHistory> {
  const res = await apiClient.put<ChatHistory>(`/chat-history/${id}`, dto);
  return res.data;
}

export async function deleteChatHistory(id: number): Promise<void> {
  await apiClient.delete(`/chat-history/${id}`);
}
