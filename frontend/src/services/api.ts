import axios from 'axios';
import type {
  CatalogProduct,
  VendorCatalog,
  LoginResponse,
  InventoryItem,
  OrderItem,
  EventLogItem,
  AlertItem,
  ReorderItem,
  ConcurrencyLabResult,
  ScenarioStatus,
  CombinedEnginesHealth,
  ConsistencyReport,
  LatencyStats,
  ConcurrencyMode,
} from '../types';

const API_BASE = '/api';

export const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach JWT access token to all outgoing requests
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('qc_access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Intercept 401 and attempt refresh token rotation
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = localStorage.getItem('qc_refresh_token');
      if (refreshToken) {
        try {
          const res = await axios.post<LoginResponse>(`${API_BASE}/auth/refresh`, { refreshToken });
          localStorage.setItem('qc_access_token', res.data.accessToken);
          localStorage.setItem('qc_refresh_token', res.data.refreshToken);
          originalRequest.headers.Authorization = `Bearer ${res.data.accessToken}`;
          return apiClient(originalRequest);
        } catch (refreshErr) {
          localStorage.removeItem('qc_access_token');
          localStorage.removeItem('qc_refresh_token');
          localStorage.removeItem('qc_user');
          window.dispatchEvent(new Event('auth:logout'));
        }
      }
    }
    return Promise.reject(error);
  }
);

// Auth Endpoints
export const authApi = {
  login: (username: string, password: string) =>
    apiClient.post<LoginResponse>('/auth/login', { username, password }),
  refresh: (refreshToken: string) =>
    apiClient.post<LoginResponse>('/auth/refresh', { refreshToken }),
  logout: (refreshToken: string) =>
    apiClient.post('/auth/logout', { refreshToken }),
  changePassword: (oldPassword: string, newPassword: string) =>
    apiClient.post('/auth/change-password', { oldPassword, newPassword }),
};

// Inventory Endpoints
export const inventoryApi = {
  getAll: (params?: { status?: string; category?: string; search?: string }) =>
    apiClient.get<InventoryItem[]>('/inventory', { params }),
  getOne: (sku: string) =>
    apiClient.get<InventoryItem>(`/inventory/${sku}`),
  adjust: (sku: string, delta: number, reason: string) =>
    apiClient.post<InventoryItem>(`/inventory/${sku}/adjust`, { delta, reason }),
  // Manager adds units to a SKU directly (no vendor involved)
  replenish: (sku: string, quantity: number, reason?: string) =>
    apiClient.post<InventoryItem>(`/inventory/${sku}/replenish`, { quantity, reason }),
};

// Manager master catalog (names/prices only) - what a vendor may order from
export const catalogApi = {
  getManagerCatalog: () => apiClient.get<CatalogProduct[]>('/catalog'),
};

// Vendor's own catalog + own stock (vendor DB)
export const vendorApi = {
  getMyCatalog: () => apiClient.get<VendorCatalog>('/vendor/catalog'),
};

// Orders Endpoints
export const ordersApi = {
  getAll: (params?: { status?: string; page?: number; size?: number }) =>
    apiClient.get<OrderItem[]>('/orders', { params }),
  create: (sku: string, quantity: number) =>
    apiClient.post('/orders', { sku, quantity }),
};

// Logs Endpoints
export const logsApi = {
  getAll: (params?: { level?: string; sku?: string; page?: number; size?: number }) =>
    apiClient.get<EventLogItem[]>('/logs', { params }),
};

// Alerts Endpoints
export const alertsApi = {
  getAll: (status?: string) =>
    apiClient.get<AlertItem[]>('/alerts', { params: { status } }),
  acknowledge: (id: number) =>
    apiClient.post<AlertItem>(`/alerts/${id}/acknowledge`),
  resolve: (id: number) =>
    apiClient.post<AlertItem>(`/alerts/${id}/resolve`),
  getLatency: () =>
    apiClient.get<LatencyStats>('/alerts/latency'),
};

// Reorders Endpoints
export const reordersApi = {
  getAll: (status?: string) =>
    apiClient.get<ReorderItem[]>('/reorders', { params: { status } }),
  create: (sku: string, quantity: number, severity?: string) =>
    apiClient.post<ReorderItem>('/reorders', { sku, quantity, severity }),
  acknowledge: (id: number) =>
    apiClient.post<ReorderItem>(`/reorders/${id}/acknowledge`),
  ship: (id: number) =>
    apiClient.post<ReorderItem>(`/reorders/${id}/ship`),
  deliver: (id: number) =>
    apiClient.post<ReorderItem>(`/reorders/${id}/deliver`),
  cancel: (id: number, reason: string) =>
    apiClient.post<ReorderItem>(`/reorders/${id}/cancel`, { reason }),
};

// Simulation Endpoints
export const simulationApi = {
  start: (eventsPerSec?: number, seed?: number) =>
    apiClient.post('/simulation/start', { eventsPerSec, seed }),
  pause: () => apiClient.post('/simulation/pause'),
  resume: () => apiClient.post('/simulation/resume'),
  stop: () => apiClient.post('/simulation/stop'),
  getStatus: () => apiClient.get('/simulation/status'),
  getScenarios: () => apiClient.get<ScenarioStatus[]>('/simulation/scenarios'),
  runScenario: (id: string) => apiClient.post<ScenarioStatus>(`/simulation/scenario/${id}`),
  getScenarioStatus: (id: string) => apiClient.get<ScenarioStatus>(`/simulation/scenario/${id}`),
};

// Concurrency Lab Endpoints
export const labApi = {
  run: (data: {
    sku: string;
    initialStock: number;
    threadCount: number;
    ordersPerThread: number;
    concurrencyMode: ConcurrencyMode;
    artificialDelayMs: number;
  }) => apiClient.post<ConcurrencyLabResult>('/lab/run', data),
  getLastResult: () => apiClient.get<ConcurrencyLabResult>('/lab/last-result'),
  setMode: (mode: ConcurrencyMode) => apiClient.post('/lab/mode', { mode }),
  setWorkers: (workerPoolSize: number) => apiClient.post('/lab/workers', { workerPoolSize }),
};

// Engines Health & Audit Endpoints
export const healthApi = {
  getCombinedHealth: () => apiClient.get<CombinedEnginesHealth>('/engines/health'),
  runConsistencyCheck: () => apiClient.post<ConsistencyReport>('/audit/consistency-check'),
  getAuditLogs: (page = 0, size = 50) => apiClient.get('/audit/logs', { params: { page, size } }),
};
