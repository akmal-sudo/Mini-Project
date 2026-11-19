export type UserRole = 'ADMIN' | 'WAREHOUSE_MANAGER' | 'VENDOR';
export type ConcurrencyMode = 'NONE' | 'LOCK' | 'DB_ONLY' | 'OPTIMISTIC';
export type OrderStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED' | 'FAILED';
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type ReorderStatus = 'CREATED' | 'ACKNOWLEDGED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface User {
  username: string;
  role: UserRole;
  userId: number;
  vendorId?: number | null;
  mustChangePassword?: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
  username: string;
  role: UserRole;
  userId: number;
  vendorId?: number | null;
  mustChangePassword: boolean;
}

export interface InventoryItem {
  sku: string;
  name: string;
  category: string;
  price: number;
  quantity: number;
  initialQuantity: number;
  reorderThreshold: number;
  reorderQuantity: number;
  status: 'OK' | 'LOW' | 'OUT_OF_STOCK';
  version: number;
  updatedAt: string;
}

/** A product of the manager's master catalog as a vendor sees it (no manager stock). */
export interface CatalogProduct {
  sku: string;
  name: string;
  category: string;
  price: number;
  inMyCatalog: boolean;
}

/** A product in a vendor's OWN catalog, with the vendor's own stock count. */
export interface VendorCatalogItem {
  sku: string;
  name: string;
  category: string;
  price: number;
  quantity: number;
  source: 'OWN' | 'MANAGER_CATALOG';
}

export interface VendorCatalog {
  vendorId: number;
  vendorName: string;
  items: VendorCatalogItem[];
}

export interface OrderItem {
  orderId: number;
  eventId: string;
  sku: string;
  quantity: number;
  status: OrderStatus;
  failureReason?: string | null;
  createdAt: string;
  processedAt?: string | null;
  processedBy?: string | null;
}

export interface EventLogItem {
  logId: number;
  eventId?: string;
  eventType?: string;
  sku?: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
  ingestedAt: string;
  createdAt: string;
}

export interface AlertItem {
  alertId: number;
  sku: string;
  pattern: string;
  severity: AlertSeverity;
  message: string;
  status: AlertStatus;
  acknowledgedBy?: number | null;
  acknowledgedAt?: string | null;
  resolvedAt?: string | null;
  sourceIngestedAt?: string | null;
  createdAt: string;
}

export interface ReorderItem {
  reorderId: number;
  sku: string;
  vendorId: number;
  quantity: number;
  priorityScore: number;
  severity: AlertSeverity;
  status: ReorderStatus;
  alertId?: number | null;
  createdAt: string;
  updatedAt: string;
  deliveredAt?: string | null;
}

export interface ConcurrencyLabResult {
  sku: string;
  initialStock: number;
  threadCount: number;
  ordersPerThread: number;
  totalOrders: number;
  concurrencyMode: ConcurrencyMode;
  artificialDelayMs: number;
  expectedFinalStock: number;
  actualFinalStock: number;
  movementsCount: number;
  lostUpdatesCount: number;
  elapsedTimeMs: number;
  lockContentionCount: number;
  avgLockWaitTimeMs: number;
  p95LockWaitTimeMs: number;
  perThreadSuccess: Record<string, number>;
  perThreadFailed: Record<string, number>;
  consistent: boolean;
}

export interface ScenarioStatus {
  scenarioId: string;
  name: string;
  status: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  description: string;
  startTime?: string | null;
  endTime?: string | null;
  totalEvents: number;
  processedEvents: number;
  results?: Record<string, any>;
  message: string;
}

export interface OsMetricsSnapshot {
  queueDepth: number;
  queueCapacity: number;
  producedCount: number;
  processedCount: number;
  failedCount: number;
  rejectedOverflowCount: number;
  activeWorkerCount: number;
  workerPoolSize: number;
  perThreadProcessedCounts: Record<string, number>;
  lockContentionCount: number;
  avgLockWaitTimeMs: number;
  p95LockWaitTimeMs: number;
  concurrencyMode: ConcurrencyMode;
  artificialDelayMs: number;
}

export interface DbmsMetricsSnapshot {
  activeConnections: number;
  maxPoolSize: number;
  inventoryConsistencyClean: boolean;
  tableRowCounts: Record<string, number>;
  uptimeSeconds: number;
}

export interface DsaMetricsSnapshot {
  cacheHitRatio: number;
  cacheHits: number;
  cacheMisses: number;
  cacheSize: number;
  windowSize: number;
  heapSize: number;
  topKSize: number;
  kmpComparisons: number;
}

export interface CombinedEnginesHealth {
  timestamp: string;
  osEngine: OsMetricsSnapshot;
  dbmsEngine: DbmsMetricsSnapshot;
  dsaEngine: DsaMetricsSnapshot;
  webEngine: Record<string, any>;
}

export interface ConsistencyReport {
  consistent: boolean;
  violations: string[];
  inconsistencyCount: number;
  timestamp: string;
}

export interface LatencyStats {
  p50Ms: number;
  p95Ms: number;
  sampleCount: number;
}
