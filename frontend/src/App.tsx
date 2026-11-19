import { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { LoginModal } from './components/LoginModal';
import { LoginView } from './components/LoginView';
import { InventoryPage } from './pages/InventoryPage';
import { VendorCatalogPage } from './pages/VendorCatalogPage';
import { OrdersPage } from './pages/OrdersPage';
import { LogsPage } from './pages/LogsPage';
import { AlertsPage } from './pages/AlertsPage';
import { ReordersPage } from './pages/ReordersPage';
import { ConcurrencyLabPage } from './pages/ConcurrencyLabPage';
import { ScenariosPage } from './pages/ScenariosPage';
import { EnginesHealthPage } from './pages/EnginesHealthPage';
import { realTimeService } from './services/websocket';
import { authApi } from './services/api';
import type { User, LoginResponse } from './types';

export default function App() {
  const [activeTab, setActiveTab] = useState<string>('inventory');
  // Always start as null so users MUST see the Login screen first when opening localhost
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);

  useEffect(() => {
    // Clear any leftover session user on cold start to enforce the login-first flow
    localStorage.removeItem('qc_user');

    // Connect WebSocket
    realTimeService.connect();
    const unsubWs = realTimeService.subscribeConnectionStatus((connected) => {
      setWsConnected(connected);
    });

    // Handle token expiration / forced logout
    const handleLogoutEvent = () => {
      setCurrentUser(null);
    };
    window.addEventListener('auth:logout', handleLogoutEvent);

    return () => {
      unsubWs();
      realTimeService.disconnect();
      window.removeEventListener('auth:logout', handleLogoutEvent);
    };
  }, []);

  const handleLogout = async () => {
    const refreshToken = localStorage.getItem('qc_refresh_token');
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch (e) {
        // Continue cleanup
      }
    }
    localStorage.removeItem('qc_access_token');
    localStorage.removeItem('qc_refresh_token');
    localStorage.removeItem('qc_user');
    setCurrentUser(null);
  };

  const handleLoginSuccess = (resp: LoginResponse) => {
    const user: User = {
      username: resp.username,
      role: resp.role,
      userId: resp.userId,
      vendorId: resp.vendorId,
      mustChangePassword: resp.mustChangePassword,
    };
    setCurrentUser(user);
    setIsLoginOpen(false);

    // Direct users to their respective interfaces upon successful login
    if (user.role === 'VENDOR') {
      // Vendor lands on its own catalog (order from the manager catalog from there)
      setActiveTab('inventory');
    } else if (user.role === 'WAREHOUSE_MANAGER') {
      // Warehouse Manager lands directly on Inventory Operations Hub
      setActiveTab('inventory');
    } else {
      // Admin lands directly on Master Health & Concurrency Console
      setActiveTab('health');
    }
  };

  // If user is not authenticated, strictly show the Login page first
  if (!currentUser) {
    return <LoginView onLoginSuccess={handleLoginSuccess} />;
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans selection:bg-blue-600/20 selection:text-slate-900">
      {/* Top Navigation */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        currentUser={currentUser}
        onOpenLogin={() => setIsLoginOpen(true)}
        onLogout={handleLogout}
        wsConnected={wsConnected}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
        {activeTab === 'inventory' &&
          (currentUser.role === 'VENDOR' ? (
            <VendorCatalogPage />
          ) : (
            <InventoryPage
              currentUser={currentUser}
              onOpenLogin={() => setIsLoginOpen(true)}
            />
          ))}
        {activeTab === 'orders' && <OrdersPage />}
        {activeTab === 'logs' && <LogsPage />}
        {activeTab === 'alerts' && <AlertsPage />}
        {activeTab === 'reorders' && <ReordersPage currentUser={currentUser} />}
        {activeTab === 'lab' && <ConcurrencyLabPage />}
        {activeTab === 'scenarios' && <ScenariosPage currentUser={currentUser} />}
        {activeTab === 'health' && <EnginesHealthPage currentUser={currentUser} />}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-4 px-6 text-center text-xs text-slate-500">
        <p>
          DarkStore Operations &bull; Real-Time Inventory &amp; Log Processing Engine
        </p>
      </footer>

      {/* Switch Role / Auth Modal */}
      <LoginModal
        isOpen={isLoginOpen}
        onClose={() => setIsLoginOpen(false)}
        onLoginSuccess={handleLoginSuccess}
      />
    </div>
  );
}
