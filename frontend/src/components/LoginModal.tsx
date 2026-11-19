import React, { useState } from 'react';
import { X, User as UserIcon, KeyRound, AlertCircle, ArrowRight } from 'lucide-react';
import { authApi } from '../services/api';
import type { LoginResponse } from '../types';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: (resp: LoginResponse) => void;
}

interface DemoRole {
  id: string;
  label: string;
  username: string;
  password: string;
  role: string;
  badge: string;
}

const DEMO_ROLES: DemoRole[] = [
  { id: 'admin', label: 'Admin', username: 'admin', password: 'Admin@123', role: 'ADMIN', badge: 'Full Access' },
  { id: 'warehouse1', label: 'Manager', username: 'warehouse1', password: 'Wh@12345', role: 'WAREHOUSE_MANAGER', badge: 'Operations' },
  { id: 'vendor1', label: 'FreshFarms', username: 'vendor1', password: 'Vendor@123', role: 'VENDOR', badge: 'Vendor 1' },
  { id: 'vendor2', label: 'Metro', username: 'vendor2', password: 'Vendor@123', role: 'VENDOR', badge: 'Vendor 2' },
  { id: 'vendor3', label: 'Aura', username: 'vendor3', password: 'Vendor@123', role: 'VENDOR', badge: 'Vendor 3' },
];

export const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onLoginSuccess }) => {
  const [selectedRole, setSelectedRole] = useState<DemoRole>(DEMO_ROLES[0]);
  const [username, setUsername] = useState(DEMO_ROLES[0].username);
  const [password, setPassword] = useState(DEMO_ROLES[0].password);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSelectRole = (r: DemoRole) => {
    setSelectedRole(r);
    setUsername(r.username);
    setPassword(r.password);
    setError(null);
  };

  const handleLogin = async (u = username, p = password) => {
    setLoading(true);
    setError(null);
    try {
      const res = await authApi.login(u, p);
      completeLogin(res.data);
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.title || 'Authentication failed';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const completeLogin = (resp: LoginResponse) => {
    localStorage.setItem('qc_access_token', resp.accessToken);
    localStorage.setItem('qc_refresh_token', resp.refreshToken);
    localStorage.setItem(
      'qc_user',
      JSON.stringify({
        username: resp.username,
        role: resp.role,
        userId: resp.userId,
        vendorId: resp.vendorId,
        mustChangePassword: false,
      })
    );
    onLoginSuccess(resp);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
      <div className="bg-white border border-slate-200 w-full max-w-md rounded-2xl shadow-2xl p-6 relative">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-700 p-1 rounded-lg transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="mb-5">
          <h3 className="text-lg font-bold text-slate-900 tracking-tight">
            Switch Account
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Select a profile to switch role or enter credentials
          </p>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start space-x-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {/* Segmented Role Selector */}
        <div className="mb-4">
          <div className="grid grid-cols-5 gap-1 p-1 bg-slate-100 rounded-xl border border-slate-200">
            {DEMO_ROLES.map((r) => {
              const active = selectedRole.id === r.id;
              return (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => handleSelectRole(r)}
                  className={`px-1.5 py-1.5 rounded-lg text-xs font-medium transition text-center truncate ${
                    active
                      ? 'bg-white text-slate-900 shadow-xs border border-slate-200 font-semibold'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {r.label}
                </button>
              );
            })}
          </div>

          <div className="mt-2.5 flex items-center justify-between text-xs px-1">
            <span className="text-slate-500">
              Role: <span className="text-slate-800 font-semibold">{selectedRole.role}</span>
            </span>
            <button
              type="button"
              onClick={() => handleLogin(selectedRole.username, selectedRole.password)}
              disabled={loading}
              className="text-blue-600 hover:text-blue-700 font-semibold flex items-center space-x-1"
            >
              <span>Quick switch</span>
              <ArrowRight className="w-3 h-3" />
            </button>
          </div>
        </div>

        {/* Credentials Form */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleLogin();
          }}
          className="space-y-3 pt-2"
        >
          <div>
            <label className="text-xs font-medium text-slate-700 block mb-1">Username</label>
            <div className="relative">
              <UserIcon className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                className="w-full bg-white border border-slate-300 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 font-mono"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-medium text-slate-700 block mb-1">Password</label>
            <div className="relative">
              <KeyRound className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full bg-white border border-slate-300 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 font-mono"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-2 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm transition flex items-center justify-center space-x-2"
          >
            {loading ? <span>Signing in...</span> : <span>Confirm & Switch Role</span>}
          </button>
        </form>
      </div>
    </div>
  );
};
