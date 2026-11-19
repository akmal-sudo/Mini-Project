import React, { useState } from 'react';
import {
  User as UserIcon,
  KeyRound,
  AlertCircle,
  Package,
  ArrowRight,
  ShieldCheck,
  Shield,
  Layers,
  Truck,
} from 'lucide-react';
import { authApi } from '../services/api';
import type { LoginResponse } from '../types';

interface LoginViewProps {
  onLoginSuccess: (resp: LoginResponse) => void;
}

interface DemoProfile {
  id: string;
  name: string;
  category: string;
  role: 'ADMIN' | 'WAREHOUSE_MANAGER' | 'VENDOR';
  username: string;
  password: string;
  destination: string;
  badge: string;
  badgeColor: string;
  icon: typeof Shield;
}

const DEMO_PROFILES: DemoProfile[] = [
  {
    id: 'admin',
    name: 'Admin',
    category: 'System Superadmin',
    role: 'ADMIN',
    username: 'admin',
    password: 'Admin@123',
    destination: 'Master Console • Concurrency Lab & Stress Tests',
    badge: 'Full Access',
    badgeColor: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    icon: Shield,
  },
  {
    id: 'warehouse1',
    name: 'Warehouse Manager',
    category: 'Operations Head',
    role: 'WAREHOUSE_MANAGER',
    username: 'warehouse1',
    password: 'Wh@12345',
    destination: 'Operations Hub • Stock Supplies & Dispatching to Vendors',
    badge: 'Operations',
    badgeColor: 'bg-blue-50 text-blue-700 border-blue-200',
    icon: Layers,
  },
  {
    id: 'vendor1',
    name: 'FreshFarms (Vendor 1)',
    category: 'Produce & Dairy Supplier',
    role: 'VENDOR',
    username: 'vendor1',
    password: 'Vendor@123',
    destination: 'Supplier Portal • Order Stock Supplies & Track Dispatch',
    badge: 'Produce & Dairy',
    badgeColor: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    icon: Truck,
  },
  {
    id: 'vendor2',
    name: 'Metro Wholesale (Vendor 2)',
    category: 'Bakery & Pantry Supplier',
    role: 'VENDOR',
    username: 'vendor2',
    password: 'Vendor@123',
    destination: 'Supplier Portal • Order Stock Supplies & Track Dispatch',
    badge: 'Bakery & Pantry',
    badgeColor: 'bg-amber-50 text-amber-700 border-amber-200',
    icon: Truck,
  },
  {
    id: 'vendor3',
    name: 'Aura Care (Vendor 3)',
    category: 'Personal Care & Home',
    role: 'VENDOR',
    username: 'vendor3',
    password: 'Vendor@123',
    destination: 'Supplier Portal • Order Stock Supplies & Track Dispatch',
    badge: 'Household & Care',
    badgeColor: 'bg-purple-50 text-purple-700 border-purple-200',
    icon: Truck,
  },
];

export const LoginView: React.FC<LoginViewProps> = ({ onLoginSuccess }) => {
  const [selectedProfile, setSelectedProfile] = useState<DemoProfile>(DEMO_PROFILES[0]);
  const [username, setUsername] = useState(DEMO_PROFILES[0].username);
  const [password, setPassword] = useState(DEMO_PROFILES[0].password);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSelectProfile = (profile: DemoProfile) => {
    setSelectedProfile(profile);
    setUsername(profile.username);
    setPassword(profile.password);
    setError(null);
  };

  const handleLogin = async (u = username, p = password) => {
    if (!u.trim() || !p.trim()) {
      setError('Please provide both username and password.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await authApi.login(u, p);
      completeLogin(res.data);
    } catch (err: any) {
      const msg =
        err.response?.data?.detail ||
        err.response?.data?.title ||
        'Authentication failed. Please verify credentials.';
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
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-between p-4 sm:p-6 lg:p-8 font-sans selection:bg-blue-600/20 selection:text-slate-900">
      {/* Background ambient lighting */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-32 left-1/2 -translate-x-1/2 w-[700px] h-[360px] bg-blue-100/60 rounded-full blur-[110px]" />
      </div>

      {/* Header */}
      <header className="max-w-5xl w-full mx-auto flex items-center justify-between py-2">
        <div className="flex items-center space-x-2.5">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center text-white shadow-sm">
            <Package className="w-4 h-4" />
          </div>
          <div className="flex items-center space-x-2">
            <span className="font-bold text-base text-slate-900 tracking-tight">DarkStore</span>
            <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200">
              Operations
            </span>
          </div>
        </div>

        <div className="flex items-center space-x-2 text-xs text-slate-600">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          <span>System Online &bull; Ready</span>
        </div>
      </header>

      {/* Main Login Interface */}
      <main className="w-full max-w-4xl mx-auto my-auto py-6">
        <div className="bg-white border border-slate-200 rounded-2xl shadow-xl shadow-slate-200/50 overflow-hidden">
          {/* Top banner / title */}
          <div className="p-6 sm:p-8 border-b border-slate-100 bg-linear-to-b from-slate-50/70 to-white">
            <div className="max-w-xl">
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                DarkStore Operations Sign In
              </h1>
              <p className="text-xs sm:text-sm text-slate-600 mt-1">
                Select your role or enter credentials to access your designated interface (Admin Console, Warehouse Hub, or Supplier Portal).
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-0 divide-y lg:divide-y-0 lg:divide-x divide-slate-200">
            {/* Left: Role Quick Selectors (Step 1) */}
            <div className="lg:col-span-7 p-6 sm:p-8 bg-slate-50/40 space-y-4">
              <div className="flex items-center justify-between mb-1">
                <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  Step 1: Choose Account Profile
                </span>
                <span className="text-[11px] text-slate-400">
                  Pre-configures login
                </span>
              </div>

              <div className="space-y-2.5">
                {DEMO_PROFILES.map((profile) => {
                  const isSelected = selectedProfile.id === profile.id;
                  const Icon = profile.icon;
                  return (
                    <button
                      key={profile.id}
                      type="button"
                      onClick={() => handleSelectProfile(profile)}
                      className={`w-full text-left p-3.5 rounded-xl border transition flex items-start space-x-3 ${
                        isSelected
                          ? 'bg-white border-blue-500 ring-2 ring-blue-500/10 shadow-sm'
                          : 'bg-white/80 border-slate-200 hover:border-slate-300 hover:bg-white'
                      }`}
                    >
                      <div
                        className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5 ${
                          isSelected
                            ? 'bg-blue-600 text-white'
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        <Icon className="w-4 h-4" />
                      </div>

                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-bold text-slate-900 truncate">
                            {profile.name}
                          </span>
                          <span
                            className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${profile.badgeColor}`}
                          >
                            {profile.badge}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-500 mt-0.5 truncate">
                          {profile.destination}
                        </p>
                        <div className="flex items-center space-x-2 mt-1.5 text-[10px] font-mono text-slate-400">
                          <span>user: <strong className="text-slate-700">{profile.username}</strong></span>
                          <span>&bull;</span>
                          <span>pass: <strong className="text-slate-700">{profile.password}</strong></span>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Right: Credential Form (Step 2) */}
            <div className="lg:col-span-5 p-6 sm:p-8 flex flex-col justify-between">
              <div>
                <div className="mb-5">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block mb-1">
                    Step 2: Sign In Credentials
                  </span>
                  <div className="p-3 rounded-xl bg-blue-50/70 border border-blue-200/80 text-xs">
                    <span className="text-blue-900 font-semibold block">
                      Target Interface:
                    </span>
                    <span className="text-blue-700 text-[11px] mt-0.5 block">
                      {selectedProfile.destination}
                    </span>
                  </div>
                </div>

                {error && (
                  <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start space-x-2">
                    <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                    <span>{error}</span>
                  </div>
                )}

                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    handleLogin(username, password);
                  }}
                  className="space-y-4"
                >
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1.5">
                      Username
                    </label>
                    <div className="relative">
                      <UserIcon className="w-4 h-4 text-slate-400 absolute left-3 top-3 pointer-events-none" />
                      <input
                        type="text"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="Enter username"
                        required
                        className="w-full bg-white border border-slate-300 rounded-xl pl-9 pr-3.5 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 font-mono transition"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1.5">
                      Password
                    </label>
                    <div className="relative">
                      <KeyRound className="w-4 h-4 text-slate-400 absolute left-3 top-3 pointer-events-none" />
                      <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="Enter password"
                        required
                        className="w-full bg-white border border-slate-300 rounded-xl pl-9 pr-3.5 py-2 text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 font-mono transition"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full mt-2 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-sm font-semibold transition flex items-center justify-center space-x-2 shadow-sm"
                  >
                    {loading ? (
                      <span>Signing in...</span>
                    ) : (
                      <>
                        <span>Sign In as {selectedProfile.name}</span>
                        <ArrowRight className="w-4 h-4" />
                      </>
                    )}
                  </button>
                </form>
              </div>

              {/* Security info */}
              <div className="mt-6 pt-4 border-t border-slate-200 flex items-center justify-center space-x-2 text-[11px] text-slate-500">
                <ShieldCheck className="w-3.5 h-3.5 text-slate-400" />
                <span>Stateless JWT &bull; Explicit Row Locks &bull; Multi-Tenant</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="max-w-5xl w-full mx-auto py-3 text-center text-xs text-slate-500">
        DarkStore Operations &bull; Real-Time Inventory &amp; Concurrency-Aware Processing Engine
      </footer>
    </div>
  );
};
