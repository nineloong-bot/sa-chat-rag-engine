import axios from 'axios';
import type { R } from '@/types';

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// Response interceptor: unwrap R<T>
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data as R;
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        // Return unwrapped data
        response.data = body.data;
        return response;
      }
      return Promise.reject(new Error(body.message || `Request failed with code ${body.code}`));
    }
    // Not an R wrapper, pass through
    return response;
  },
  (error) => {
    if (error.response) {
      const status = error.response.status;
      if (status === 429) {
        return Promise.reject(new Error('请求过于频繁，请稍后重试'));
      }
      return Promise.reject(new Error(`服务异常 (${status})，请稍后重试`));
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请检查网络连接'));
    }
    return Promise.reject(new Error('网络连接异常，请检查服务是否启动'));
  }
);

export default apiClient;
