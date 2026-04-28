import apiClient from './client';
import type { DocumentTaskStatus, DocumentEntity } from '@/types';

export async function getCompletedDocuments(): Promise<DocumentEntity[]> {
  const res = await apiClient.get<DocumentEntity[]>('/documents');
  return res.data;
}

export async function uploadDocument(file: File): Promise<DocumentTaskStatus> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await apiClient.post<DocumentTaskStatus>('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
  return res.data;
}

export async function getTaskStatus(taskId: string): Promise<DocumentTaskStatus> {
  const res = await apiClient.get<DocumentTaskStatus>(`/documents/task/${taskId}`);
  return res.data;
}
