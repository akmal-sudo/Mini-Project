import React, { useState, useEffect, useRef } from 'react';
import {
  Play,
  Pause,
  RotateCcw,
  Zap,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';
import { simulationApi } from '../services/api';
import type { ScenarioStatus, User } from '../types';

interface ScenariosPageProps {
  currentUser?: User | null;
}

export const ScenariosPage: React.FC<ScenariosPageProps> = ({ currentUser }) => {
  const [scenarios, setScenarios] = useState<ScenarioStatus[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeScenarioId, setActiveScenarioId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Global Simulation Controls
  const [simSpeed, setSimSpeed] = useState<number>(100);
  const [simSeed, setSimSeed] = useState<number>(42);
  const [simState, setSimState] = useState<'IDLE' | 'RUNNING' | 'PAUSED'>('IDLE');
  const [simMessage, setSimMessage] = useState<string>('Simulator is idle');
  const [actionLoading, setActionLoading] = useState(false);

  const pollIntervalRef = useRef<any>(null);

  const fetchScenarios = async () => {
    try {
      const res = await simulationApi.getScenarios();
      setScenarios(res.data);

      const running = res.data.find((s) => s.status === 'RUNNING');
      if (running) {
        setActiveScenarioId(running.scenarioId);
      } else {
        setActiveScenarioId(null);
      }
    } catch (err: any) {
      console.error('Failed to load scenarios', err);
    }
  };

  const fetchSimStatus = async () => {
    try {
      const res = await simulationApi.getStatus();
      if (res.data) {
        if (res.data.running) {
          setSimState(res.data.paused ? 'PAUSED' : 'RUNNING');
        } else {
          setSimState('IDLE');
        }
        if (res.data.message) {
          setSimMessage(res.data.message);
        }
      }
    } catch (err) {
      // Ignored
    }
  };

  useEffect(() => {
    fetchScenarios();
    fetchSimStatus();

    pollIntervalRef.current = setInterval(() => {
      fetchScenarios();
      fetchSimStatus();
    }, 2000);

    return () => {
      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    };
  }, []);

  const handleRunScenario = async (scenarioId: string) => {
    setError(null);
    setLoading(true);
    setActiveScenarioId(scenarioId);
    try {
      const res = await simulationApi.runScenario(scenarioId);
      setScenarios((prev) =>
        prev.map((s) => (s.scenarioId === scenarioId ? res.data : s))
      );
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.title || 'Failed to trigger scenario';
      setError(msg);
      setActiveScenarioId(null);
    } finally {
      setLoading(false);
    }
  };

  const handleStartSim = async () => {
    setActionLoading(true);
    setError(null);
    try {
      await simulationApi.start(simSpeed, simSeed);
      setSimState('RUNNING');
      setSimMessage(`Replaying dataset events at ${simSpeed} events/sec`);
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to start simulation');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePauseSim = async () => {
    setActionLoading(true);
    try {
      await simulationApi.pause();
      setSimState('PAUSED');
      setSimMessage('Simulation stream paused');
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to pause simulation');
    } finally {
      setActionLoading(false);
    }
  };

  const handleResumeSim = async () => {
    setActionLoading(true);
    try {
      await simulationApi.resume();
      setSimState('RUNNING');
      setSimMessage(`Simulation resumed at ${simSpeed} events/sec`);
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to resume simulation');
    } finally {
      setActionLoading(false);
    }
  };

  const handleStopSim = async () => {
    setActionLoading(true);
    try {
      await simulationApi.stop();
      setSimState('IDLE');
      setSimMessage('Simulation stopped');
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to stop simulation');
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'RUNNING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-blue-50 text-blue-700 border border-blue-200">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
            <span>RUNNING</span>
          </span>
        );
      case 'COMPLETED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span>COMPLETED</span>
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-mono font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
            <span>FAILED</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-mono text-slate-700 bg-slate-100 border border-slate-200">
            <span>IDLE</span>
          </span>
        );
    }
  };

  const getScenarioTags = (id: string) => {
    switch (id) {
      case 'flash_sale':
        return ['High Concurrency', 'Burst Ingestion', '5 SKUs', 'Row Locks'];
      case 'stock_out_cascade':
        return ['Alert Rule 1', 'Reorder Dispatch', 'Threshold Breaches'];
      case 'concurrency_stress':
        return ['NONE vs LOCK', 'Lost Updates Proof', '50 Threads', 'Race Window'];
      case 'supplier_delay':
        return ['Alert Rule 3', 'Lead Time Breach', 'Vendor Notification'];
      case 'mixed_day':
        return ['Full Pipeline', '10,000 Events', 'KMP Parsing', 'Sliding Window'];
      default:
        return ['Simulation'];
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Banner & Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center space-x-3 mb-1">
            <div className="p-2 rounded-lg bg-blue-50 text-blue-600 border border-blue-200">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-slate-900 tracking-tight">Simulation & Scenario Suite</h1>
              <p className="text-xs text-slate-500 mt-0.5">
                Execute pre-scripted burst workloads and live historical replay to observe concurrency dynamics and pipeline reaction
              </p>
            </div>
          </div>
        </div>

        {/* Global Simulation Stream Controls */}
        <div className="flex flex-wrap items-center gap-3 bg-slate-50 p-2.5 rounded-lg border border-slate-200">
          <div className="flex items-center space-x-2">
            <span className="text-xs text-slate-600 font-mono">Speed:</span>
            <input
              type="range"
              min="10"
              max="500"
              step="10"
              value={simSpeed}
              onChange={(e) => setSimSpeed(Number(e.target.value))}
              disabled={simState === 'RUNNING'}
              className="w-24 accent-blue-600 cursor-pointer"
            />
            <span className="text-xs font-mono text-blue-600 font-bold w-16">{simSpeed} evt/s</span>
          </div>

          <div className="flex items-center space-x-2 border-l border-slate-200 pl-3">
            <span className="text-xs text-slate-600 font-mono">Seed:</span>
            <input
              type="number"
              value={simSeed}
              onChange={(e) => setSimSeed(Number(e.target.value))}
              disabled={simState === 'RUNNING'}
              className="w-16 px-2 py-1 text-xs bg-white border border-slate-300 rounded text-slate-800 font-mono text-center focus:outline-none focus:border-blue-500"
            />
          </div>

          <div className="flex items-center space-x-1.5 border-l border-slate-200 pl-3">
            {simState === 'IDLE' && (
              <button
                onClick={handleStartSim}
                disabled={actionLoading}
                className="flex items-center space-x-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold shadow-xs transition disabled:opacity-50"
              >
                <Play className="w-3.5 h-3.5 fill-current" />
                <span>Start Stream</span>
              </button>
            )}

            {simState === 'RUNNING' && (
              <>
                <button
                  onClick={handlePauseSim}
                  disabled={actionLoading}
                  className="flex items-center space-x-1 px-2.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
                >
                  <Pause className="w-3.5 h-3.5 fill-current" />
                  <span>Pause</span>
                </button>
                <button
                  onClick={handleStopSim}
                  disabled={actionLoading}
                  className="flex items-center space-x-1 px-2.5 py-1.5 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Stop</span>
                </button>
              </>
            )}

            {simState === 'PAUSED' && (
              <>
                <button
                  onClick={handleResumeSim}
                  disabled={actionLoading}
                  className="flex items-center space-x-1 px-2.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
                >
                  <Play className="w-3.5 h-3.5 fill-current" />
                  <span>Resume</span>
                </button>
                <button
                  onClick={handleStopSim}
                  disabled={actionLoading}
                  className="flex items-center space-x-1 px-2.5 py-1.5 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Stop</span>
                </button>
              </>
            )}
          </div>
        </div>

        {/* Status Line */}
        <div className="w-full mt-3 pt-2 border-t border-slate-200 flex flex-wrap items-center justify-between text-[11px] font-mono text-slate-500">
          <div className="flex items-center space-x-2">
            <span className="text-blue-600 font-semibold">Simulator State:</span>
            <span className="text-slate-800">{simMessage}</span>
          </div>
          {currentUser && (
            <div className="text-slate-500">
              Operator: <span className="text-slate-800 font-semibold">{currentUser.username}</span> ({currentUser.role})
            </div>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-xl text-sm flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <AlertCircle className="w-4 h-4 text-rose-600" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-xs text-rose-600 font-semibold hover:underline">
            Dismiss
          </button>
        </div>
      )}

      {/* Scenarios Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {scenarios.map((scenario) => {
          const isRunning = scenario.status === 'RUNNING';
          const tags = getScenarioTags(scenario.scenarioId);
          const percent =
            scenario.totalEvents > 0
              ? Math.min(100, Math.round((scenario.processedEvents / scenario.totalEvents) * 100))
              : isRunning
              ? 50
              : 0;

          return (
            <div
              key={scenario.scenarioId}
              className={`flex flex-col justify-between bg-white rounded-xl border transition-all ${
                isRunning
                  ? 'border-blue-400 shadow-md shadow-blue-500/10'
                  : 'border-slate-200 hover:border-slate-300 shadow-xs'
              } p-5`}
            >
              <div>
                <div className="flex items-start justify-between gap-2 mb-3">
                  <h3 className="text-sm font-bold text-slate-900">{scenario.name}</h3>
                  {getStatusBadge(scenario.status)}
                </div>

                <p className="text-xs text-slate-600 mb-4 leading-relaxed min-h-[40px]">
                  {scenario.description}
                </p>

                {/* Tags */}
                <div className="flex flex-wrap gap-1.5 mb-4">
                  {tags.map((t, idx) => (
                    <span
                      key={idx}
                      className="text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-600 font-mono border border-slate-200"
                    >
                      {t}
                    </span>
                  ))}
                </div>

                {/* Progress bar if running or completed */}
                {(isRunning || scenario.totalEvents > 0) && (
                  <div className="space-y-1.5 mb-4 bg-slate-50 p-3 rounded-lg border border-slate-200">
                    <div className="flex items-center justify-between text-[11px] font-mono text-slate-600">
                      <span>Events Processed</span>
                      <span className="text-blue-700 font-bold">
                        {scenario.processedEvents.toLocaleString()} / {scenario.totalEvents.toLocaleString()}
                      </span>
                    </div>
                    <div className="w-full h-1.5 bg-slate-200 rounded-full overflow-hidden">
                      <div
                        className={`h-full transition-all duration-300 ${
                          isRunning ? 'bg-blue-600' : 'bg-emerald-600'
                        }`}
                        style={{ width: `${percent}%` }}
                      ></div>
                    </div>
                  </div>
                )}

                {/* Results Summary if any */}
                {scenario.results && Object.keys(scenario.results).length > 0 && (
                  <div className="mb-4 bg-slate-50 rounded-lg p-3 border border-slate-200 text-xs font-mono space-y-1">
                    <div className="text-[10px] uppercase font-bold text-slate-500 mb-1">
                      Scenario Telemetry
                    </div>
                    {Object.entries(scenario.results).map(([k, v]) => (
                      <div key={k} className="flex justify-between items-center text-slate-700">
                        <span className="text-slate-500">{k}:</span>
                        <span className="text-blue-700 font-bold">{String(v)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Action Button & Message */}
              <div className="pt-3 border-t border-slate-100 flex items-center justify-between">
                <span className="text-[11px] text-slate-500 font-mono truncate max-w-[150px]">
                  {scenario.message || 'Ready'}
                </span>
                <button
                  onClick={() => handleRunScenario(scenario.scenarioId)}
                  disabled={loading || activeScenarioId !== null}
                  className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                    isRunning
                      ? 'bg-blue-50 text-blue-700 border border-blue-200 cursor-not-allowed'
                      : 'bg-blue-600 hover:bg-blue-700 text-white shadow-xs disabled:opacity-50'
                  }`}
                >
                  {isRunning ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      <span>Executing...</span>
                    </>
                  ) : (
                    <>
                      <Play className="w-3.5 h-3.5 fill-current" />
                      <span>Run Scenario</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
