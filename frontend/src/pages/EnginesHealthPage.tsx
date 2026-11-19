import React, { useState, useEffect } from 'react';
import { Activity, Database, Cpu, Globe, Server, CheckCircle2, AlertTriangle, RefreshCw, Sliders, Shield } from 'lucide-react';
import { healthApi, labApi } from '../services/api';
import { realTimeService } from '../services/websocket';
import type { CombinedEnginesHealth, ConsistencyReport, User, ConcurrencyMode } from '../types';

interface EnginesHealthPageProps {
  currentUser: User | null;
}

export const EnginesHealthPage: React.FC<EnginesHealthPageProps> = ({ currentUser }) => {
  const [health, setHealth] = useState<CombinedEnginesHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [auditReport, setAuditReport] = useState<ConsistencyReport | null>(null);
  const [auditRunning, setAuditRunning] = useState(false);

  // Admin dynamic resize & mode
  const [newPoolSize, setNewPoolSize] = useState(8);
  const [newMode, setNewMode] = useState<ConcurrencyMode>('LOCK');
  const [adminSaving, setAdminSaving] = useState(false);

  const loadHealth = async () => {
    try {
      const res = await healthApi.getCombinedHealth();
      setHealth(res.data);
      setNewPoolSize(res.data.osEngine.workerPoolSize);
      setNewMode(res.data.osEngine.concurrencyMode);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHealth();

    const unsubscribe = realTimeService.onOsMetrics((metrics) => {
      setHealth((prev) => (prev ? { ...prev, osEngine: metrics } : null));
    });

    const interval = setInterval(loadHealth, 3000);
    return () => {
      unsubscribe();
      clearInterval(interval);
    };
  }, []);

  const handleRunAudit = async () => {
    setAuditRunning(true);
    try {
      const res = await healthApi.runConsistencyCheck();
      setAuditReport(res.data);
    } catch (e: any) {
      alert(`Audit error: ${e.response?.data?.detail || e.message}`);
    } finally {
      setAuditRunning(false);
    }
  };

  const handleApplyOsConfig = async () => {
    setAdminSaving(true);
    try {
      await Promise.all([
        labApi.setWorkers(newPoolSize),
        labApi.setMode(newMode),
      ]);
      loadHealth();
    } catch (e: any) {
      alert(`Error updating OS configuration: ${e.response?.data?.detail || e.message}`);
    } finally {
      setAdminSaving(false);
    }
  };

  if (loading || !health) {
    return (
      <div className="p-12 text-center text-slate-500 font-mono text-xs">
        Loading 4-Engine telemetry snapshots...
      </div>
    );
  }

  const { osEngine, dbmsEngine, dsaEngine, webEngine } = health;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center space-x-2">
            <Activity className="w-5 h-5 text-blue-600" />
            <span>Engines Health & Architecture Telemetry</span>
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Real-time live telemetry snapshots across OS, DBMS, DSA, and Web API sub-engines
          </p>
        </div>

        <button
          onClick={loadHealth}
          className="flex items-center space-x-1 px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 rounded-lg text-xs font-medium shadow-xs transition"
        >
          <RefreshCw className="w-3.5 h-3.5 text-slate-500" />
          <span>Refresh</span>
        </button>
      </div>

      {/* 4 Engine Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* ENGINE 1: OS ENGINE */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-200 pb-3">
            <div className="flex items-center space-x-2.5">
              <div className="p-2 rounded-lg bg-blue-50 text-blue-600 border border-blue-200">
                <Cpu className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Engine 1: OS Concurrency Engine</h3>
                <p className="text-[11px] text-slate-500 font-mono">BlockingQueue & Worker Pool</p>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-blue-50 text-blue-700 border border-blue-200">
              MODE: {osEngine.concurrencyMode}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2 font-mono text-xs">
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Queue Depth</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">
                {osEngine.queueDepth} <span className="text-[10px] text-slate-400">/ {osEngine.queueCapacity}</span>
              </div>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Active Workers</div>
              <div className="text-base font-bold text-emerald-600 mt-0.5">
                {osEngine.activeWorkerCount} <span className="text-[10px] text-slate-400">/ {osEngine.workerPoolSize}</span>
              </div>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Contention</div>
              <div className="text-base font-bold text-amber-600 mt-0.5">{osEngine.lockContentionCount}</div>
            </div>
          </div>

          <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 font-mono text-[11px] space-y-1">
            <div className="flex justify-between">
              <span className="text-slate-600">Produced Events:</span>
              <span className="text-slate-900 font-semibold">{osEngine.producedCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Processed Events:</span>
              <span className="text-emerald-700 font-semibold">{osEngine.processedCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Rejected Overflow (Backpressure):</span>
              <span className="text-amber-700 font-semibold">{osEngine.rejectedOverflowCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">P95 Lock Wait Time:</span>
              <span className="text-slate-900 font-semibold">{osEngine.p95LockWaitTimeMs.toFixed(2)} ms</span>
            </div>
          </div>

          {/* Admin Runtime Controls */}
          {currentUser?.role === 'ADMIN' && (
            <div className="pt-2 border-t border-slate-200 space-y-2">
              <div className="text-[11px] font-semibold text-slate-700 flex items-center space-x-1">
                <Sliders className="w-3.5 h-3.5 text-blue-600" />
                <span>Admin Runtime Reconfiguration</span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div>
                  <label className="text-[10px] text-slate-500 block mb-0.5">Worker Pool Size (1-64)</label>
                  <input
                    type="number"
                    min="1"
                    max="64"
                    value={newPoolSize}
                    onChange={(e) => setNewPoolSize(parseInt(e.target.value) || 8)}
                    className="w-full bg-white border border-slate-300 rounded px-2.5 py-1 text-slate-800 font-mono text-xs focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="text-[10px] text-slate-500 block mb-0.5">Sync Mode</label>
                  <select
                    value={newMode}
                    onChange={(e) => setNewMode(e.target.value as ConcurrencyMode)}
                    className="w-full bg-white border border-slate-300 rounded px-2 py-1 text-slate-800 font-mono text-xs focus:outline-none focus:border-blue-500"
                  >
                    <option value="LOCK">LOCK (Default)</option>
                    <option value="NONE">NONE (Naive)</option>
                    <option value="DB_ONLY">DB_ONLY</option>
                    <option value="OPTIMISTIC">OPTIMISTIC</option>
                  </select>
                </div>
              </div>
              <button
                onClick={handleApplyOsConfig}
                disabled={adminSaving}
                className="w-full py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
              >
                {adminSaving ? 'Applying...' : 'Apply OS Changes'}
              </button>
            </div>
          )}
        </div>

        {/* ENGINE 2: DBMS ENGINE */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-200 pb-3">
            <div className="flex items-center space-x-2.5">
              <div className="p-2 rounded-lg bg-emerald-50 text-emerald-600 border border-emerald-200">
                <Database className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Engine 2: PostgreSQL DBMS Engine</h3>
                <p className="text-[11px] text-slate-500 font-mono">Flyway, Views & PL/pgSQL Triggers</p>
              </div>
            </div>
            <span
              className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold border ${
                dbmsEngine.inventoryConsistencyClean
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                  : 'bg-rose-50 text-rose-700 border-rose-200'
              }`}
            >
              {dbmsEngine.inventoryConsistencyClean ? 'CONSISTENCY: CLEAN' : 'CONSISTENCY: MISMATCH'}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 font-mono text-xs">
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Connection Pool</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">
                {dbmsEngine.activeConnections} <span className="text-[10px] text-slate-400">/ {dbmsEngine.maxPoolSize}</span>
              </div>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Database Engine</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">PostgreSQL 15</div>
            </div>
          </div>

          <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 font-mono text-[11px] grid grid-cols-2 gap-x-4 gap-y-1">
            <div className="flex justify-between">
              <span className="text-slate-600">Products:</span>
              <span className="text-slate-900 font-semibold">{dbmsEngine.tableRowCounts?.products ?? 50}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Movements:</span>
              <span className="text-slate-900 font-semibold">{dbmsEngine.tableRowCounts?.inventory_movements ?? 0}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Orders:</span>
              <span className="text-slate-900 font-semibold">{dbmsEngine.tableRowCounts?.orders ?? 0}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Reorders:</span>
              <span className="text-slate-900 font-semibold">{dbmsEngine.tableRowCounts?.reorder_requests ?? 0}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Alerts:</span>
              <span className="text-slate-900 font-semibold">{dbmsEngine.tableRowCounts?.alerts ?? 0}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Event Logs:</span>
              <span className="text-slate-900 font-semibold">{dbmsEngine.tableRowCounts?.event_logs ?? 0}</span>
            </div>
          </div>

          {/* Consistency Audit Run */}
          <div className="pt-2 border-t border-slate-200 space-y-2">
            <button
              onClick={handleRunAudit}
              disabled={auditRunning}
              className="w-full py-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold flex items-center justify-center space-x-1.5 shadow-xs transition"
            >
              <Shield className="w-3.5 h-3.5" />
              <span>{auditRunning ? 'Auditing Ledger Consistency...' : 'Run Consistency Audit (v_inventory_consistency)'}</span>
            </button>

            {auditReport && (
              <div
                className={`p-3 rounded-lg text-xs border font-mono ${
                  auditReport.consistent
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                    : 'bg-rose-50 border-rose-200 text-rose-900'
                }`}
              >
                <div className="font-bold flex items-center space-x-1.5">
                  {auditReport.consistent ? <CheckCircle2 className="w-4 h-4 text-emerald-600" /> : <AlertTriangle className="w-4 h-4 text-rose-600" />}
                  <span>{auditReport.consistent ? '100% Consistent: All ledger invariants hold' : `Discrepancies Found: ${auditReport.inconsistencyCount}`}</span>
                </div>
                {!auditReport.consistent && (
                  <ul className="list-disc pl-4 mt-1 text-[11px] space-y-0.5 text-rose-700">
                    {auditReport.violations.map((v, i) => (
                      <li key={i}>{v}</li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        </div>

        {/* ENGINE 3: DSA ENGINE */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-200 pb-3">
            <div className="flex items-center space-x-2.5">
              <div className="p-2 rounded-lg bg-purple-50 text-purple-600 border border-purple-200">
                <Server className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Engine 3: DSA In-Memory Engine</h3>
                <p className="text-[11px] text-slate-500 font-mono">Custom Heap, KMP, Cache, Window</p>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-purple-50 text-purple-700 border border-purple-200">
              HIT RATIO: {(dsaEngine.cacheHitRatio * 100).toFixed(1)}%
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2 font-mono text-xs">
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Inventory Cache</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">{dsaEngine.cacheSize} SKUs</div>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Priority Heap</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">{dsaEngine.heapSize} items</div>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Sliding Window</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">{dsaEngine.windowSize} events</div>
            </div>
          </div>

          <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 font-mono text-[11px] space-y-1">
            <div className="flex justify-between">
              <span className="text-slate-600">Cache Hits / Misses:</span>
              <span className="text-slate-900 font-semibold">{dsaEngine.cacheHits} / {dsaEngine.cacheMisses}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Top-K Min-Heap Size:</span>
              <span className="text-slate-900 font-semibold">{dsaEngine.topKSize} items</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">KMP String Comparisons:</span>
              <span className="text-slate-900 font-semibold">{dsaEngine.kmpComparisons} operations</span>
            </div>
          </div>
        </div>

        {/* ENGINE 4: WEB & API ENGINE */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-200 pb-3">
            <div className="flex items-center space-x-2.5">
              <div className="p-2 rounded-lg bg-blue-50 text-blue-600 border border-blue-200">
                <Globe className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Engine 4: Web & API Engine</h3>
                <p className="text-[11px] text-slate-500 font-mono">Stateless JWT, STOMP & RFC 7807</p>
              </div>
            </div>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              STATUS: UP
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 font-mono text-xs">
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Rate Limiter (Token Bucket)</div>
              <div className="text-base font-bold text-emerald-700 mt-0.5">100 req/min</div>
            </div>
            <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              <div className="text-[10px] text-slate-500">Session Architecture</div>
              <div className="text-base font-bold text-slate-900 mt-0.5">100% Stateless</div>
            </div>
          </div>

          <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 font-mono text-[11px] space-y-1">
            <div className="flex justify-between">
              <span className="text-slate-600">JVM Heap Memory:</span>
              <span className="text-slate-900 font-semibold">
                {Math.round((webEngine.heapMemoryUsedBytes || 0) / 1024 / 1024)} MB /{' '}
                {Math.round((webEngine.heapMemoryMaxBytes || 0) / 1024 / 1024)} MB
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">JVM Threads:</span>
              <span className="text-slate-900 font-semibold">{webEngine.jvmThreadCount || 0} active</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">System Uptime:</span>
              <span className="text-emerald-700 font-semibold">
                {Math.floor((webEngine.jvmUptimeMs || 0) / 60000)} min
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
