import React, { useState, useEffect } from 'react';
import { Cpu, Zap, AlertOctagon, CheckCircle2, RefreshCw, Clock, Flame, BarChart3 } from 'lucide-react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, Legend } from 'recharts';
import { labApi, inventoryApi } from '../services/api';
import type { ConcurrencyLabResult, ConcurrencyMode, InventoryItem } from '../types';

export const ConcurrencyLabPage: React.FC = () => {
  const [catalog, setCatalog] = useState<InventoryItem[]>([]);
  const [sku, setSku] = useState('SKU-MILK-01');
  const [initialStock, setInitialStock] = useState(1000);
  const [threadCount, setThreadCount] = useState(50);
  const [ordersPerThread, setOrdersPerThread] = useState(20);
  const [mode, setMode] = useState<ConcurrencyMode>('LOCK');
  const [delayMs, setDelayMs] = useState(0);

  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<ConcurrencyLabResult | null>(null);

  useEffect(() => {
    inventoryApi.getAll().then((res) => {
      setCatalog(res.data);
      if (res.data.length > 0) setSku(res.data[0].sku);
    }).catch(console.error);

    labApi.getLastResult().then((res) => {
      if (res.data) setResult(res.data);
    }).catch(() => {});
  }, []);

  const handleLaunchBurst = async (e: React.FormEvent) => {
    e.preventDefault();
    setRunning(true);
    try {
      const res = await labApi.run({
        sku,
        initialStock,
        threadCount,
        ordersPerThread,
        concurrencyMode: mode,
        artificialDelayMs: delayMs,
      });
      setResult(res.data);
    } catch (err: any) {
      alert(`Concurrency test error: ${err.response?.data?.detail || err.message}`);
    } finally {
      setRunning(false);
    }
  };

  const chartData = result
    ? [
        {
          name: 'Final Inventory Stock',
          Expected: result.expectedFinalStock,
          Actual: result.actualFinalStock,
          LostUpdates: result.lostUpdatesCount,
        },
      ]
    : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center space-x-2">
            <Cpu className="w-5 h-5 text-blue-600" />
            <span>Concurrency Lab & Race Condition Simulator</span>
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Stress-test shared inventory stock counters across multi-threaded worker pools with millisecond synchronization
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Configuration Panel */}
        <div className="bg-white border border-slate-200 p-5 rounded-xl shadow-xs space-y-4">
          <div className="flex items-center space-x-2 text-sm font-bold text-slate-900 border-b border-slate-200 pb-3">
            <Zap className="w-4 h-4 text-blue-600" />
            <span>Burst Configuration</span>
          </div>

          <form onSubmit={handleLaunchBurst} className="space-y-4 text-xs">
            <div>
              <label className="text-slate-700 font-medium block mb-1">Target SKU</label>
              <select
                value={sku}
                onChange={(e) => setSku(e.target.value)}
                className="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-800 font-mono focus:outline-none focus:border-blue-500"
              >
                {catalog.map((c) => (
                  <option key={c.sku} value={c.sku}>
                    {c.sku} ({c.name})
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="text-slate-700 font-medium block mb-1">Initial Stock</label>
                <input
                  type="number"
                  min="10"
                  value={initialStock}
                  onChange={(e) => setInitialStock(parseInt(e.target.value) || 1000)}
                  className="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-800 font-mono focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="text-slate-700 font-medium block mb-1">Threads</label>
                <input
                  type="number"
                  min="1"
                  max="64"
                  value={threadCount}
                  onChange={(e) => setThreadCount(parseInt(e.target.value) || 50)}
                  className="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-800 font-mono focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="text-slate-700 font-medium block mb-1">Orders/Th</label>
                <input
                  type="number"
                  min="1"
                  value={ordersPerThread}
                  onChange={(e) => setOrdersPerThread(parseInt(e.target.value) || 20)}
                  className="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-800 font-mono focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>

            <div className="text-[11px] text-slate-600 font-mono bg-slate-50 p-2.5 rounded-lg border border-slate-200">
              Total Burst: <span className="text-blue-600 font-bold">{threadCount * ordersPerThread} orders</span> released simultaneously with CountDownLatch.
            </div>

            {/* Concurrency Strategy Selection */}
            <div>
              <label className="text-slate-700 font-medium block mb-2">Concurrency Strategy</label>
              <div className="space-y-2">
                {[
                  {
                    id: 'NONE',
                    label: 'NONE (Naive Read-Modify-Write)',
                    desc: 'Demonstrates lost updates / overselling',
                    danger: true,
                  },
                  {
                    id: 'LOCK',
                    label: 'LOCK (Default: Per-SKU Lock + DB Row Lock)',
                    desc: 'Strict serializability, 0 anomalies',
                    primary: true,
                  },
                  {
                    id: 'DB_ONLY',
                    label: 'DB_ONLY (SELECT ... FOR UPDATE)',
                    desc: 'Database row-level exclusive locks',
                  },
                  {
                    id: 'OPTIMISTIC',
                    label: 'OPTIMISTIC (JPA Version + Jitter Backoff)',
                    desc: 'Version checking with bounded retries',
                  },
                ].map((strat) => (
                  <label
                    key={strat.id}
                    className={`block p-2.5 rounded-lg border cursor-pointer transition ${
                      mode === strat.id
                        ? strat.danger
                          ? 'bg-rose-50 border-rose-300 text-rose-900'
                          : 'bg-blue-50 border-blue-300 text-blue-900'
                        : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
                    }`}
                  >
                    <div className="flex items-center space-x-2">
                      <input
                        type="radio"
                        name="mode"
                        value={strat.id}
                        checked={mode === strat.id}
                        onChange={() => setMode(strat.id as ConcurrencyMode)}
                        className="text-blue-600 focus:ring-0"
                      />
                      <span className="font-semibold text-xs text-slate-900">{strat.label}</span>
                    </div>
                    <p className="text-[10px] text-slate-500 mt-1 pl-5">{strat.desc}</p>
                  </label>
                ))}
              </div>
            </div>

            {/* Artificial Delay */}
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="text-slate-700 font-medium">Artificial Race Delay</label>
                <span className="text-blue-600 font-mono text-xs font-semibold">{delayMs} ms</span>
              </div>
              <input
                type="range"
                min="0"
                max="10"
                step="1"
                value={delayMs}
                onChange={(e) => setDelayMs(parseInt(e.target.value) || 0)}
                className="w-full accent-blue-600 cursor-pointer"
              />
              <p className="text-[10px] text-slate-500 mt-0.5">
                Widens the time window between read and write to highlight race conditions in NONE mode
              </p>
            </div>

            <button
              type="submit"
              disabled={running}
              className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-xs transition flex items-center justify-center space-x-2"
            >
              {running ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin text-white" />
                  <span>Executing 50-Thread Burst...</span>
                </>
              ) : (
                <>
                  <Flame className="w-4 h-4 text-white" />
                  <span>Execute Concurrency Test</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Results Panel */}
        <div className="lg:col-span-2 space-y-4">
          {result ? (
            <>
              {/* Verdict Banner */}
              <div
                className={`p-4 rounded-xl border flex items-center space-x-3 shadow-xs ${
                  result.consistent
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                    : 'bg-rose-50 border-rose-200 text-rose-900'
                }`}
              >
                {result.consistent ? (
                  <CheckCircle2 className="w-6 h-6 text-emerald-600 shrink-0" />
                ) : (
                  <AlertOctagon className="w-6 h-6 text-rose-600 shrink-0" />
                )}
                <div>
                  <h3 className="text-sm font-bold uppercase tracking-wider font-mono">
                    {result.consistent
                      ? 'Strict Serializability Maintained'
                      : 'Race Condition Detected: Lost Updates Produced'}
                  </h3>
                  <p className="text-xs text-slate-600 mt-0.5">
                    {result.consistent
                      ? `Zero lost updates across ${result.totalOrders} concurrent operations under ${result.concurrencyMode} mode.`
                      : `Naive read-modify-write caused threads to overwrite each other's updates, losing ${result.lostUpdatesCount} inventory deductions!`}
                  </p>
                </div>
              </div>

              {/* Metrics Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
                  <div className="text-xs text-slate-500 font-medium">Expected Stock</div>
                  <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{result.expectedFinalStock}</div>
                  <div className="text-[10px] text-slate-500 mt-1 font-mono">Max(0, {result.initialStock} - {result.totalOrders})</div>
                </div>

                <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
                  <div className="text-xs text-slate-500 font-medium">Actual Stock</div>
                  <div className={`text-2xl font-bold mt-1 font-mono ${result.actualFinalStock === result.expectedFinalStock ? 'text-emerald-600' : 'text-rose-600'}`}>
                    {result.actualFinalStock}
                  </div>
                  <div className="text-[10px] text-slate-500 mt-1">From PostgreSQL</div>
                </div>

                <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
                  <div className="text-xs text-slate-500 font-medium font-mono">Lost Updates</div>
                  <div className={`text-2xl font-bold mt-1 font-mono ${result.lostUpdatesCount > 0 ? 'text-rose-600' : 'text-emerald-600'}`}>
                    {result.lostUpdatesCount}
                  </div>
                  <div className="text-[10px] text-slate-500 mt-1">
                    {result.lostUpdatesCount > 0 ? 'Overselling anomaly' : 'Zero anomalies'}
                  </div>
                </div>

                <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
                  <div className="text-xs text-slate-500 font-medium flex items-center space-x-1">
                    <Clock className="w-3.5 h-3.5 text-blue-600" />
                    <span>Duration</span>
                  </div>
                  <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{result.elapsedTimeMs} ms</div>
                  <div className="text-[10px] text-slate-500 mt-1">{result.movementsCount} DB movements</div>
                </div>
              </div>

              {/* Lock Telemetry */}
              <div className="grid grid-cols-3 gap-3 bg-white border border-slate-200 p-4 rounded-xl shadow-xs text-xs font-mono">
                <div>
                  <div className="text-slate-500 text-[11px]">Lock Contention Count</div>
                  <div className="text-lg font-bold text-amber-600 mt-0.5">{result.lockContentionCount}</div>
                </div>
                <div>
                  <div className="text-slate-500 text-[11px]">Avg Lock Wait Time</div>
                  <div className="text-lg font-bold text-slate-800 mt-0.5">{result.avgLockWaitTimeMs.toFixed(2)} ms</div>
                </div>
                <div>
                  <div className="text-slate-500 text-[11px]">P95 Lock Wait Time</div>
                  <div className="text-lg font-bold text-slate-800 mt-0.5">{result.p95LockWaitTimeMs.toFixed(2)} ms</div>
                </div>
              </div>

              {/* Visual Stock Comparison Bar Chart */}
              <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
                <div className="text-xs font-bold text-slate-800 mb-3 flex items-center space-x-1.5">
                  <BarChart3 className="w-4 h-4 text-blue-600" />
                  <span>Inventory Consistency Comparison (Expected vs Actual vs Lost Updates)</span>
                </div>
                <div className="h-60 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                      <XAxis dataKey="name" stroke="#94a3b8" />
                      <YAxis stroke="#94a3b8" />
                      <Tooltip
                        contentStyle={{ backgroundColor: '#ffffff', borderColor: '#e2e8f0', borderRadius: '8px', fontSize: '12px', color: '#0f172a' }}
                      />
                      <Legend wrapperStyle={{ fontSize: '11px', paddingTop: '8px' }} />
                      <Bar dataKey="Expected" fill="#3B82F6" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="Actual" fill="#10B981" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="LostUpdates" fill="#EF4444" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </>
          ) : (
            <div className="bg-white border border-slate-200 rounded-xl p-12 text-center text-slate-400 space-y-3 shadow-xs">
              <Cpu className="w-10 h-10 mx-auto text-slate-400" />
              <div className="text-sm font-semibold text-slate-800">No test executed yet</div>
              <p className="text-xs max-w-sm mx-auto text-slate-500">
                Configure your SKU, thread count, and concurrency mode on the left and click "Execute Concurrency Test" to measure race conditions.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
