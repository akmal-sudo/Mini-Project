import React, { useState, useEffect } from 'react';
import { CheckCircle, Clock, ShieldAlert, Check, RefreshCw, Send, AlertTriangle } from 'lucide-react';
import { alertsApi } from '../services/api';
import { realTimeService } from '../services/websocket';
import type { AlertItem, LatencyStats } from '../types';

export const AlertsPage: React.FC = () => {
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [latency, setLatency] = useState<LatencyStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'ACTIVE' | 'RESOLVED'>('ACTIVE');

  const loadData = async () => {
    setLoading(true);
    try {
      const [alertsRes, latRes] = await Promise.all([
        alertsApi.getAll(),
        alertsApi.getLatency(),
      ]);
      setAlerts(alertsRes.data);
      setLatency(latRes.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();

    const unsubscribe = realTimeService.onAlert((newAlert) => {
      setAlerts((prev) => [newAlert, ...prev.filter((a) => a.alertId !== newAlert.alertId)]);
    });

    return () => {
      unsubscribe();
    };
  }, []);

  const handleAcknowledge = async (id: number) => {
    try {
      await alertsApi.acknowledge(id);
      loadData();
    } catch (e) {
      console.error(e);
    }
  };

  const handleResolve = async (id: number) => {
    try {
      await alertsApi.resolve(id);
      loadData();
    } catch (e) {
      console.error(e);
    }
  };

  const activeAlerts = alerts.filter((a) => a.status === 'OPEN' || a.status === 'ACKNOWLEDGED');
  const resolvedAlerts = alerts.filter((a) => a.status === 'RESOLVED');

  const displayedAlerts = tab === 'ACTIVE' ? activeAlerts : resolvedAlerts;

  const renderSeverityBadge = (sev: string) => {
    switch (sev) {
      case 'CRITICAL':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200 font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
            CRITICAL
          </span>
        );
      case 'HIGH':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200 font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            HIGH
          </span>
        );
      case 'MEDIUM':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-yellow-50 text-yellow-800 border border-yellow-200 font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-yellow-500" />
            MEDIUM
          </span>
        );
      case 'LOW':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-blue-50 text-blue-700 border border-blue-200 font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
            LOW
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-700 border border-slate-200 font-mono">
            {sev}
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center space-x-2">
            <ShieldAlert className="w-5 h-5 text-rose-600" />
            <span>Alert Pipeline & Webhook Monitor</span>
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Real-time anomaly detection with HMAC-signed webhooks and automated replenishment triggers
          </p>
        </div>

        <button
          onClick={loadData}
          className="flex items-center space-x-1 px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 rounded-lg text-xs font-medium shadow-xs transition"
        >
          <RefreshCw className="w-3.5 h-3.5 text-slate-500" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Latency & Telemetry Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-slate-500 font-medium flex items-center space-x-1.5">
            <Clock className="w-3.5 h-3.5 text-blue-600" />
            <span>Detection Latency (p50)</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">
            {latency ? `${latency.p50Ms.toFixed(1)} ms` : '—'}
          </div>
          <div className="text-[11px] text-slate-500 mt-1">Ingest to alert creation time</div>
        </div>

        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-slate-500 font-medium flex items-center space-x-1.5">
            <Clock className="w-3.5 h-3.5 text-purple-600" />
            <span>Detection Latency (p95)</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">
            {latency ? `${latency.p95Ms.toFixed(1)} ms` : '—'}
          </div>
          <div className="text-[11px] text-slate-500 mt-1">95th percentile SLA &lt; 200ms</div>
        </div>

        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-slate-500 font-medium flex items-center space-x-1.5">
            <Send className="w-3.5 h-3.5 text-emerald-600" />
            <span>HMAC Webhooks</span>
          </div>
          <div className="text-lg font-bold text-emerald-700 mt-1 font-mono">SHA-256 SIGNED</div>
          <div className="text-[11px] text-slate-500 mt-1">Header: X-QC-Signature</div>
        </div>

        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-slate-500 font-medium flex items-center space-x-1.5">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
            <span>Active Incidents</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{activeAlerts.length}</div>
          <div className="text-[11px] text-slate-500 mt-1">{resolvedAlerts.length} historically resolved</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center space-x-1 bg-slate-100 p-1 rounded-xl border border-slate-200 w-fit">
        <button
          onClick={() => setTab('ACTIVE')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition flex items-center space-x-1.5 ${
            tab === 'ACTIVE'
              ? 'bg-white text-slate-900 shadow-xs border border-slate-200 font-semibold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <span>Active Incidents</span>
          <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-rose-100 text-rose-700 font-mono font-semibold">
            {activeAlerts.length}
          </span>
        </button>
        <button
          onClick={() => setTab('RESOLVED')}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium transition flex items-center space-x-1.5 ${
            tab === 'RESOLVED'
              ? 'bg-white text-slate-900 shadow-xs border border-slate-200 font-semibold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          <span>Resolved Archive</span>
          <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-slate-200 text-slate-700 font-mono font-semibold">
            {resolvedAlerts.length}
          </span>
        </button>
      </div>

      {/* Alerts Grid / Table */}
      <div className="space-y-3">
        {loading ? (
          <div className="p-8 text-center text-slate-500 font-mono text-xs">
            Loading alerts...
          </div>
        ) : displayedAlerts.length === 0 ? (
          <div className="p-8 bg-white border border-slate-200 rounded-xl text-center text-slate-500 text-xs shadow-xs">
            {tab === 'ACTIVE'
              ? 'All clear. No active alert incidents in the system.'
              : 'No resolved alerts recorded yet.'}
          </div>
        ) : (
          displayedAlerts.map((alert) => (
            <div
              key={alert.alertId}
              className="bg-white border border-slate-200 hover:border-slate-300 p-4 rounded-xl flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-xs transition"
            >
              <div className="space-y-1.5">
                <div className="flex items-center space-x-2">
                  {renderSeverityBadge(alert.severity)}
                  <span className="text-xs font-mono font-semibold text-slate-800">{alert.sku}</span>
                  <span className="text-[11px] font-mono text-slate-500">Pattern: {alert.pattern}</span>
                </div>
                <div className="text-xs text-slate-900 font-semibold">{alert.message}</div>
                <div className="text-[11px] text-slate-500 font-mono">
                  Triggered: {new Date(alert.createdAt).toLocaleString()}
                  {alert.acknowledgedAt && ` • Acked: ${new Date(alert.acknowledgedAt).toLocaleTimeString()}`}
                  {alert.resolvedAt && ` • Resolved: ${new Date(alert.resolvedAt).toLocaleTimeString()}`}
                </div>
              </div>

              {/* Actions */}
              {tab === 'ACTIVE' && (
                <div className="flex items-center space-x-2 shrink-0">
                  {alert.status === 'OPEN' && (
                    <button
                      onClick={() => handleAcknowledge(alert.alertId)}
                      className="px-3 py-1.5 bg-white hover:bg-slate-50 border border-slate-300 text-slate-700 rounded-lg text-xs font-semibold flex items-center space-x-1 shadow-xs transition"
                    >
                      <Check className="w-3.5 h-3.5 text-blue-600" />
                      <span>Acknowledge</span>
                    </button>
                  )}
                  <button
                    onClick={() => handleResolve(alert.alertId)}
                    className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold flex items-center space-x-1 shadow-xs transition"
                  >
                    <CheckCircle className="w-3.5 h-3.5" />
                    <span>Resolve</span>
                  </button>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
};
