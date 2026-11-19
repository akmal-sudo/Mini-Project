import React, { useState, useEffect, useRef } from 'react';
import { Terminal, Pause, Play, Trash2, ArrowDown, Search } from 'lucide-react';
import { logsApi } from '../services/api';
import { realTimeService } from '../services/websocket';
import type { EventLogItem } from '../types';

export const LogsPage: React.FC = () => {
  const [logs, setLogs] = useState<EventLogItem[]>([]);
  const [levelFilter, setLevelFilter] = useState('ALL');
  const [skuSearch, setSkuSearch] = useState('');
  const [isPaused, setIsPaused] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);

  const terminalEndRef = useRef<HTMLDivElement>(null);

  // Initial load
  useEffect(() => {
    logsApi.getAll({ size: 100 }).then((res) => {
      setLogs(res.data.reverse()); // oldest to newest
    }).catch(console.error);

    // Subscribe to real-time logs over WebSocket
    const unsubscribe = realTimeService.onLog((newLog) => {
      if (!isPaused) {
        setLogs((prev) => [...prev.slice(-300), newLog]);
      }
    });

    return () => {
      unsubscribe();
    };
  }, [isPaused]);

  // Auto-scroll logic
  useEffect(() => {
    if (autoScroll && terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  const filteredLogs = logs.filter((log) => {
    if (levelFilter !== 'ALL' && log.level !== levelFilter) return false;
    if (skuSearch && log.sku && !log.sku.toLowerCase().includes(skuSearch.toLowerCase())) return false;
    return true;
  });

  const highlightKmpPatterns = (message: string) => {
    const patterns = ['STOCK_OUT', 'LOW_STOCK', 'ORDER_FAILED', 'RESTOCK_FAILED', 'out of stock', 'insufficient stock'];
    let rendered: React.ReactNode = message;

    patterns.forEach((pat) => {
      if (typeof rendered === 'string' && rendered.includes(pat)) {
        const parts = rendered.split(pat);
        rendered = (
          <>
            {parts[0]}
            <span className="px-1.5 py-0.5 rounded bg-amber-100 text-amber-900 font-semibold border border-amber-300 text-[11px]">
              {pat}
            </span>
            {parts.slice(1).join(pat)}
          </>
        );
      }
    });

    return rendered;
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center space-x-2">
            <Terminal className="w-5 h-5 text-blue-600" />
            <span>Real-Time Log Streamer</span>
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            EBNF-parsed log stream with Knuth-Morris-Pratt (KMP) pattern signature detection
          </p>
        </div>

        {/* Stream Controls */}
        <div className="flex items-center space-x-2">
          <button
            onClick={() => setIsPaused(!isPaused)}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border shadow-xs transition ${
              isPaused
                ? 'bg-amber-50 text-amber-800 border-amber-300'
                : 'bg-white hover:bg-slate-50 text-slate-700 border-slate-300'
            }`}
          >
            {isPaused ? <Play className="w-3.5 h-3.5 text-amber-600" /> : <Pause className="w-3.5 h-3.5 text-slate-500" />}
            <span>{isPaused ? 'Resume Stream' : 'Pause Stream'}</span>
          </button>

          <button
            onClick={() => setAutoScroll(!autoScroll)}
            className={`flex items-center space-x-1 px-3 py-1.5 rounded-lg text-xs font-medium border shadow-xs transition ${
              autoScroll
                ? 'bg-blue-50 text-blue-700 border-blue-200'
                : 'bg-white text-slate-600 border-slate-300 hover:bg-slate-50'
            }`}
          >
            <ArrowDown className="w-3.5 h-3.5" />
            <span>Auto-Scroll</span>
          </button>

          <button
            onClick={() => setLogs([])}
            className="p-1.5 bg-white hover:bg-rose-50 text-slate-500 hover:text-rose-600 border border-slate-300 rounded-lg shadow-xs transition"
            title="Clear Buffer"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Filter Header */}
      <div className="bg-white border border-slate-200 p-3 rounded-xl flex flex-wrap items-center justify-between gap-3 text-xs shadow-xs">
        <div className="flex items-center space-x-1.5">
          <span className="text-slate-500 font-mono text-[11px] mr-1">Level:</span>
          {['ALL', 'INFO', 'WARN', 'ERROR'].map((lvl) => (
            <button
              key={lvl}
              onClick={() => setLevelFilter(lvl)}
              className={`px-2.5 py-1 rounded-md font-mono text-[11px] font-medium transition ${
                levelFilter === lvl
                  ? lvl === 'ERROR'
                    ? 'bg-rose-50 text-rose-700 border border-rose-200 font-bold'
                    : lvl === 'WARN'
                    ? 'bg-amber-50 text-amber-700 border border-amber-200 font-bold'
                    : 'bg-blue-50 text-blue-700 border border-blue-200 font-bold'
                  : 'bg-slate-50 text-slate-600 hover:text-slate-900 border border-slate-200'
              }`}
            >
              {lvl}
            </button>
          ))}
        </div>

        <div className="relative w-48">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2" />
          <input
            type="text"
            placeholder="Filter SKU..."
            value={skuSearch}
            onChange={(e) => setSkuSearch(e.target.value)}
            className="w-full bg-white border border-slate-300 rounded-lg pl-8 pr-2.5 py-1 text-xs text-slate-900 focus:outline-none focus:border-blue-500 font-mono"
          />
        </div>
      </div>

      {/* Terminal View (Light IDE Console) */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
        <div className="bg-slate-50 border-b border-slate-200 px-4 py-2 flex items-center justify-between text-xs text-slate-600 font-mono">
          <div className="flex items-center space-x-2">
            <span className="w-2.5 h-2.5 rounded-full bg-slate-300 inline-block" />
            <span className="w-2.5 h-2.5 rounded-full bg-slate-300 inline-block" />
            <span className="w-2.5 h-2.5 rounded-full bg-slate-300 inline-block" />
            <span className="text-slate-600 text-[11px] font-medium ml-2">darkstore-events.stdout</span>
          </div>
          <span className="text-[11px] text-slate-500 font-medium">{filteredLogs.length} buffered lines</span>
        </div>

        <div className="p-4 font-mono text-xs h-[500px] overflow-y-auto space-y-1.5 bg-white">
          {filteredLogs.length === 0 ? (
            <div className="h-full flex items-center justify-center text-slate-400 text-xs">
              Waiting for incoming event logs...
            </div>
          ) : (
            filteredLogs.map((log) => {
              const levelColor =
                log.level === 'ERROR'
                  ? 'text-rose-700 bg-rose-50 border-rose-200 font-semibold'
                  : log.level === 'WARN'
                  ? 'text-amber-700 bg-amber-50 border-amber-200 font-semibold'
                  : 'text-blue-700 bg-blue-50 border-blue-200 font-semibold';

              return (
                <div
                  key={log.logId}
                  className="flex items-start space-x-2.5 py-0.5 hover:bg-slate-50 px-1.5 rounded transition"
                >
                  <span className="text-slate-400 select-none shrink-0 text-[11px]">
                    {new Date(log.createdAt).toLocaleTimeString()}
                  </span>

                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold border uppercase shrink-0 ${levelColor}`}>
                    {log.level}
                  </span>

                  {log.sku && (
                    <span className="text-slate-700 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200 shrink-0 text-[11px] font-medium">
                      {log.sku}
                    </span>
                  )}

                  <span className="text-slate-800 leading-relaxed break-all">
                    {highlightKmpPatterns(log.message)}
                  </span>
                </div>
              );
            })
          )}
          <div ref={terminalEndRef} />
        </div>
      </div>
    </div>
  );
};
