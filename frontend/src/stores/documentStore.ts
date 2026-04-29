import { create } from 'zustand';
import type { DocumentTaskStatus } from '@/types';
import { uploadDocument as uploadDocApi, getTaskStatus } from '@/api/document';

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'DEAD']);
const POLL_INTERVAL_MS = 2000;

interface DocumentState {
  uploading: boolean;
  tasks: DocumentTaskStatus[];

  uploadDocument: (file: File) => Promise<void>;
  pollTask: (taskId: string) => Promise<void>;
  updateTask: (status: DocumentTaskStatus) => void;
}

export const useDocumentStore = create<DocumentState>((set) => ({
  uploading: false,
  tasks: [],

  uploadDocument: async (file: File) => {
    set({ uploading: true });
    try {
      const status = await uploadDocApi(file);
      set((s) => ({ tasks: [status, ...s.tasks] }));
      startPolling(status.taskId, set);
    } finally {
      set({ uploading: false });
    }
  },

  pollTask: async (taskId: string) => {
    const updated = await getTaskStatus(taskId);
    set((s) => ({
      tasks: s.tasks.map((t) => (t.taskId === updated.taskId ? updated : t)),
    }));
  },

  updateTask: (status: DocumentTaskStatus) => {
    set((s) => ({
      tasks: s.tasks.map((t) => (t.taskId === status.taskId ? status : t)),
    }));
  },
}));

/**
 * 轮询任务状态直到到达终态。
 */
function startPolling(
  taskId: string,
  set: (fn: (s: DocumentState) => Partial<DocumentState>) => void,
): void {
  const poll = async () => {
    try {
      const updated = await getTaskStatus(taskId);
      set((s) => ({
        tasks: s.tasks.map((t) => (t.taskId === updated.taskId ? updated : t)),
      }));
      if (!TERMINAL_STATUSES.has(updated.status)) {
        setTimeout(poll, POLL_INTERVAL_MS);
      }
    } catch {
      // Polling error is non-critical, will retry on next interval
    }
  };
  setTimeout(poll, POLL_INTERVAL_MS);
}
