import { create } from 'zustand';
import type { DocumentTaskStatus, DocumentEntity } from '@/types';
import { uploadDocument as uploadDocApi, getTaskStatus } from '@/api/document';

interface DocumentState {
  uploading: boolean;
  tasks: DocumentTaskStatus[];
  documents: DocumentEntity[];

  uploadDocument: (file: File) => Promise<void>;
  pollTask: (taskId: string) => Promise<void>;
  updateTask: (status: DocumentTaskStatus) => void;
}

export const useDocumentStore = create<DocumentState>((set, get) => ({
  uploading: false,
  tasks: [],
  documents: [],

  uploadDocument: async (file: File) => {
    set({ uploading: true });
    try {
      const status = await uploadDocApi(file);
      set((s) => ({ tasks: [status, ...s.tasks] }));
      // Start polling
      const poll = async () => {
        const updated = await getTaskStatus(status.taskId);
        set((s) => ({
          tasks: s.tasks.map((t) => (t.taskId === updated.taskId ? updated : t)),
        }));
        if (updated.status === 'PROCESSING' || updated.status === 'PENDING' || updated.status === 'QUEUED') {
          setTimeout(poll, 2000);
        }
      };
      setTimeout(poll, 1000);
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
