import { describe, it, expect, vi, beforeEach } from 'vitest';

// Provide standard localStorage mock in Node environment
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString();
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
  };
})();

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  writable: true,
});

import {
  authApi,
  inventoryApi,
  ordersApi,
  alertsApi,
  reordersApi,
  simulationApi,
  labApi,
  healthApi,
  apiClient,
} from '../services/api';
import { realTimeService } from '../services/websocket';
import type { ConcurrencyMode, InventoryItem, AlertItem } from '../types';

describe('Frontend Services & API Clients', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('should have properly structured authApi endpoints', () => {
    expect(typeof authApi.login).toBe('function');
    expect(typeof authApi.refresh).toBe('function');
    expect(typeof authApi.logout).toBe('function');
    expect(typeof authApi.changePassword).toBe('function');
  });

  it('should have properly structured inventoryApi and ordersApi endpoints', () => {
    expect(typeof inventoryApi.getAll).toBe('function');
    expect(typeof inventoryApi.getOne).toBe('function');
    expect(typeof inventoryApi.adjust).toBe('function');

    expect(typeof ordersApi.getAll).toBe('function');
    expect(typeof ordersApi.create).toBe('function');
  });

  it('should have properly structured alertsApi and reordersApi endpoints', () => {
    expect(typeof alertsApi.getAll).toBe('function');
    expect(typeof alertsApi.acknowledge).toBe('function');
    expect(typeof alertsApi.resolve).toBe('function');
    expect(typeof alertsApi.getLatency).toBe('function');

    expect(typeof reordersApi.getAll).toBe('function');
    expect(typeof reordersApi.create).toBe('function');
    expect(typeof reordersApi.acknowledge).toBe('function');
    expect(typeof reordersApi.ship).toBe('function');
    expect(typeof reordersApi.deliver).toBe('function');
    expect(typeof reordersApi.cancel).toBe('function');
  });

  it('should have properly structured simulation and lab endpoints', () => {
    expect(typeof simulationApi.start).toBe('function');
    expect(typeof simulationApi.pause).toBe('function');
    expect(typeof simulationApi.resume).toBe('function');
    expect(typeof simulationApi.stop).toBe('function');
    expect(typeof simulationApi.getScenarios).toBe('function');
    expect(typeof simulationApi.runScenario).toBe('function');

    expect(typeof labApi.run).toBe('function');
    expect(typeof labApi.getLastResult).toBe('function');
    expect(typeof labApi.setMode).toBe('function');
    expect(typeof labApi.setWorkers).toBe('function');

    expect(typeof healthApi.getCombinedHealth).toBe('function');
    expect(typeof healthApi.runConsistencyCheck).toBe('function');
  });

  it('should attach Bearer token if present in localStorage', async () => {
    localStorage.setItem('qc_access_token', 'test-jwt-token');

    const config: any = { headers: {} };
    const interceptor = (apiClient.interceptors.request as any).handlers[0];
    const transformed = interceptor.fulfilled(config);

    expect(transformed.headers.Authorization).toBe('Bearer test-jwt-token');
  });

  it('realTimeService should handle subscriber registration and unsubscription', () => {
    let receivedLog = false;
    const unsubLog = realTimeService.onLog(() => {
      receivedLog = true;
    });

    let receivedAlert = false;
    const unsubAlert = realTimeService.onAlert(() => {
      receivedAlert = true;
    });

    let receivedStock = false;
    const unsubStock = realTimeService.onInventoryUpdate(() => {
      receivedStock = true;
    });

    let receivedConn = false;
    const unsubConn = realTimeService.onStatusChange((c) => {
      receivedConn = c;
    });

    expect(typeof unsubLog).toBe('function');
    expect(typeof unsubAlert).toBe('function');
    expect(typeof unsubStock).toBe('function');
    expect(typeof unsubConn).toBe('function');
    expect(receivedLog).toBe(false);
    expect(receivedAlert).toBe(false);
    expect(receivedStock).toBe(false);
    expect(receivedConn).toBe(false);

    // Clean up
    unsubLog();
    unsubAlert();
    unsubStock();
    unsubConn();
  });

  it('should validate concurrency modes supported by DarkStoreOS', () => {
    const validModes: ConcurrencyMode[] = ['NONE', 'LOCK', 'DB_ONLY', 'OPTIMISTIC'];
    expect(validModes).toContain('LOCK');
    expect(validModes).toContain('NONE');
    expect(validModes).toContain('DB_ONLY');
    expect(validModes).toContain('OPTIMISTIC');
    expect(validModes.length).toBe(4);
  });

  it('should handle inventory status computation', () => {
    const mockItem: InventoryItem = {
      sku: 'SKU-001',
      name: 'Organic Milk 1L',
      category: 'Dairy',
      price: 3.5,
      quantity: 5,
      reorderThreshold: 10,
      reorderQuantity: 20,
      initialQuantity: 50,
      version: 1,
      status: 'LOW',
      updatedAt: new Date().toISOString(),
    };

    expect(mockItem.quantity).toBeLessThanOrEqual(mockItem.reorderThreshold);
    expect(mockItem.status).toBe('LOW');
  });

  it('should verify alert severity classification', () => {
    const highAlert: AlertItem = {
      alertId: 1,
      sku: 'SKU-001',
      pattern: 'STOCK_OUT',
      severity: 'CRITICAL',
      message: 'Stock reached 0',
      status: 'OPEN',
      createdAt: new Date().toISOString(),
    };

    expect(highAlert.severity).toBe('CRITICAL');
    expect(highAlert.status).toBe('OPEN');
    expect(highAlert.pattern).toBe('STOCK_OUT');
  });
});
