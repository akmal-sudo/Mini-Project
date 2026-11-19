import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { OsMetricsSnapshot, AlertItem, ReorderItem, EventLogItem, InventoryItem } from '../types';

export type WebSocketStatusListener = (connected: boolean) => void;

class RealTimeService {
  private client: Client | null = null;
  private isConnected = false;
  private statusListeners: Set<WebSocketStatusListener> = new Set();
  private pollingTimer: any = null;

  private inventoryListeners: Set<(item: InventoryItem) => void> = new Set();
  private alertListeners: Set<(alert: AlertItem) => void> = new Set();
  private reorderListeners: Set<(reorder: ReorderItem) => void> = new Set();
  private logListeners: Set<(log: EventLogItem) => void> = new Set();
  private metricsListeners: Set<(metrics: OsMetricsSnapshot) => void> = new Set();

  public connect() {
    if (this.client && this.client.active) {
      return;
    }

    try {
      this.client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: () => {},
        onConnect: () => {
          this.isConnected = true;
          this.stopPollingFallback();
          this.notifyStatus(true);
          this.subscribeTopics();
        },
        onDisconnect: () => {
          this.isConnected = false;
          this.notifyStatus(false);
          this.startPollingFallback();
        },
        onStompError: () => {
          this.isConnected = false;
          this.notifyStatus(false);
          this.startPollingFallback();
        },
        onWebSocketClose: () => {
          this.isConnected = false;
          this.notifyStatus(false);
          this.startPollingFallback();
        },
      });

      this.client.activate();
    } catch (e) {
      this.startPollingFallback();
    }
  }

  private subscribeTopics() {
    if (!this.client || !this.client.connected) return;

    this.client.subscribe('/topic/inventory-updates', (message) => {
      try {
        const data = JSON.parse(message.body);
        this.inventoryListeners.forEach((l) => l(data));
      } catch (e) {}
    });

    this.client.subscribe('/topic/alerts', (message) => {
      try {
        const data = JSON.parse(message.body);
        this.alertListeners.forEach((l) => l(data));
      } catch (e) {}
    });

    this.client.subscribe('/topic/reorders', (message) => {
      try {
        const data = JSON.parse(message.body);
        this.reorderListeners.forEach((l) => l(data));
      } catch (e) {}
    });

    this.client.subscribe('/topic/logs', (message) => {
      try {
        const data = JSON.parse(message.body);
        this.logListeners.forEach((l) => l(data));
      } catch (e) {}
    });

    this.client.subscribe('/topic/os-metrics', (message) => {
      try {
        const data = JSON.parse(message.body);
        this.metricsListeners.forEach((l) => l(data));
      } catch (e) {}
    });
  }

  // Fallback: 3-second polling mechanism when WebSocket disconnects
  private startPollingFallback() {
    if (this.pollingTimer) return;
    this.pollingTimer = setInterval(async () => {
      try {
        const res = await fetch('/api/engines/health');
        if (res.ok) {
          const data = await res.json();
          if (data?.osEngine) {
            this.metricsListeners.forEach((l) => l(data.osEngine));
          }
        }
      } catch (e) {
        // quiet error
      }
    }, 3000);
  }

  private stopPollingFallback() {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }
  }

  public onStatusChange(listener: WebSocketStatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.isConnected);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  public subscribeConnectionStatus(listener: WebSocketStatusListener): () => void {
    return this.onStatusChange(listener);
  }

  public onInventoryUpdate(listener: (item: InventoryItem) => void): () => void {
    this.inventoryListeners.add(listener);
    return () => {
      this.inventoryListeners.delete(listener);
    };
  }

  public onAlert(listener: (alert: AlertItem) => void): () => void {
    this.alertListeners.add(listener);
    return () => {
      this.alertListeners.delete(listener);
    };
  }

  public onReorder(listener: (reorder: ReorderItem) => void): () => void {
    this.reorderListeners.add(listener);
    return () => {
      this.reorderListeners.delete(listener);
    };
  }

  public onLog(listener: (log: EventLogItem) => void): () => void {
    this.logListeners.add(listener);
    return () => {
      this.logListeners.delete(listener);
    };
  }

  public onOsMetrics(listener: (metrics: OsMetricsSnapshot) => void): () => void {
    this.metricsListeners.add(listener);
    return () => {
      this.metricsListeners.delete(listener);
    };
  }

  private notifyStatus(connected: boolean) {
    this.statusListeners.forEach((l) => l(connected));
  }

  public disconnect() {
    this.stopPollingFallback();
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.isConnected = false;
    this.notifyStatus(false);
  }
}

export const realTimeService = new RealTimeService();
