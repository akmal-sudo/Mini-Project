import React from 'react';
import {
  Package,
  ShoppingCart,
  Terminal,
  AlertTriangle,
  RefreshCw,
  Cpu,
  Activity,
  PlayCircle,
  LogIn,
  LogOut,
  Truck,
  Shield,
  Layers,
} from 'lucide-react';
import type { User } from '../types';

interface NavbarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  currentUser: User | null;
  onOpenLogin: () => void;
  onLogout: () => void;
  wsConnected: boolean;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeTab,
  setActiveTab,
  currentUser,
  onOpenLogin,
  onLogout,
  wsConnected,
}) => {
  // Build role-specific navigation tabs
  const getNavItems = () => {
    if (!currentUser) {
      return [
        { id: 'inventory', label: 'Inventory', icon: Package },
        { id: 'orders', label: 'Orders', icon: ShoppingCart },
        { id: 'alerts', label: 'Alerts', icon: AlertTriangle },
      ];
    }

    if (currentUser.role === 'VENDOR') {
      return [
        { id: 'inventory', label: 'My Catalog', icon: Package },
        { id: 'reorders', label: 'My Supply Orders', icon: RefreshCw },
      ];
    }

    if (currentUser.role === 'WAREHOUSE_MANAGER') {
      return [
        { id: 'inventory', label: 'Inventory Hub', icon: Package },
        { id: 'orders', label: 'Customer Orders', icon: ShoppingCart },
        { id: 'reorders', label: 'Vendor Orders', icon: RefreshCw },
        { id: 'alerts', label: 'Stock Alerts', icon: AlertTriangle },
        { id: 'logs', label: 'Audit Logs', icon: Terminal },
      ];
    }

    // ADMIN has full access to all 8 operational and testing modules
    return [
      { id: 'health', label: 'System Health', icon: Activity },
      { id: 'inventory', label: 'Inventory Hub', icon: Package },
      { id: 'orders', label: 'Orders Queue', icon: ShoppingCart },
      { id: 'reorders', label: 'Stock Supplies', icon: RefreshCw },
      { id: 'lab', label: 'Concurrency Lab', icon: Cpu },
      { id: 'scenarios', label: 'Scenarios', icon: PlayCircle },
      { id: 'alerts', label: 'Alerts', icon: AlertTriangle },
      { id: 'logs', label: 'Audit Logs', icon: Terminal },
    ];
  };

  const navItems = getNavItems();

  const getRoleBadge = () => {
    if (!currentUser) return null;
    if (currentUser.role === 'VENDOR') {
      const vendorName = `Vendor V${currentUser.vendorId ?? ''}`;
      return {
        portalName: 'Supplier Portal',
        tag: vendorName,
        badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200',
        icon: Truck,
      };
    }
    if (currentUser.role === 'WAREHOUSE_MANAGER') {
      return {
        portalName: 'Warehouse Ops',
        tag: 'Manager',
        badgeClass: 'bg-blue-50 text-blue-700 border-blue-200',
        icon: Layers,
      };
    }
    return {
      portalName: 'Admin Console',
      tag: 'Superadmin',
      badgeClass: 'bg-indigo-50 text-indigo-700 border-indigo-200',
      icon: Shield,
    };
  };

  const roleInfo = getRoleBadge();

  const handleBrandClick = () => {
    if (!currentUser) {
      setActiveTab('inventory');
    } else if (currentUser.role === 'VENDOR') {
      setActiveTab('inventory');
    } else if (currentUser.role === 'WAREHOUSE_MANAGER') {
      setActiveTab('inventory');
    } else {
      setActiveTab('health');
    }
  };

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200 px-4 py-2.5 shadow-sm">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Brand & Interface Header */}
        <div
          className="flex items-center space-x-2.5 cursor-pointer select-none"
          onClick={handleBrandClick}
          title="Go to Home"
        >
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center text-white shadow-sm">
            <Package className="w-4 h-4" />
          </div>
          <div>
            <div className="flex items-center space-x-1.5">
              <span className="font-bold text-base text-slate-900 tracking-tight">DarkStore</span>
              {roleInfo ? (
                <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-md border ${roleInfo.badgeClass}`}>
                  {roleInfo.portalName}
                </span>
              ) : (
                <span className="text-[10px] font-semibold px-1.5 py-0.2 rounded bg-slate-100 text-slate-700 border border-slate-200">
                  OPS
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Navigation Tabs (Filtered strictly for role) */}
        <nav className="hidden lg:flex items-center space-x-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                  isActive
                    ? 'bg-slate-100 text-slate-900 font-semibold border border-slate-200 shadow-xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>

        {/* Right Section: Live Stream Status & User Actions */}
        <div className="flex items-center space-x-3">
          {/* Live Indicator */}
          <div
            className="flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-medium border border-slate-200 bg-slate-50"
            title={wsConnected ? 'WebSocket Live Stream Connected' : 'Polling Fallback Active'}
          >
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                wsConnected ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'
              }`}
            />
            <span className="text-[11px] text-slate-600">{wsConnected ? 'Live' : 'Polling'}</span>
          </div>

          {/* User Profile */}
          {currentUser ? (
            <div className="flex items-center space-x-2">
              <div className="hidden sm:flex items-center space-x-2 border border-slate-200 bg-slate-50 px-2.5 py-1 rounded-lg">
                <div className="w-5 h-5 rounded-full bg-white border border-slate-200 flex items-center justify-center text-[10px] font-bold text-slate-700">
                  {currentUser.username.charAt(0).toUpperCase()}
                </div>
                <span className="text-xs text-slate-800 font-medium">{currentUser.username}</span>
                {roleInfo && (
                  <span className={`text-[10px] px-1.5 py-0.5 rounded-md font-medium border ${roleInfo.badgeClass}`}>
                    {roleInfo.tag}
                  </span>
                )}
              </div>

              <button
                onClick={onOpenLogin}
                className="px-2.5 py-1 bg-white hover:bg-slate-50 border border-slate-300 text-slate-700 rounded-lg text-xs font-medium shadow-xs transition"
                title="Switch active user profile"
              >
                Switch Role
              </button>

              <button
                onClick={onLogout}
                className="p-1.5 text-slate-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                title="Sign out & return to login"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <button
              onClick={onOpenLogin}
              className="flex items-center space-x-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold shadow-sm transition"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Sign In</span>
            </button>
          )}
        </div>
      </div>

      {/* Mobile Navigation */}
      <div className="lg:hidden flex items-center space-x-1 overflow-x-auto pt-2 mt-2 border-t border-slate-200">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`flex items-center space-x-1 px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap ${
                isActive ? 'bg-slate-100 text-slate-900 font-semibold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Icon className="w-3 h-3" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>
    </header>
  );
};
