import React, { useState, useEffect } from 'react';
import {
  Search,
  RefreshCw,
  PackagePlus,
  Sliders,
  CheckCircle2,
  ShoppingCart,
  X,
} from 'lucide-react';
import { inventoryApi, ordersApi } from '../services/api';
import { realTimeService } from '../services/websocket';
import type { InventoryItem, User } from '../types';

interface InventoryPageProps {
  currentUser: User | null;
  onOpenLogin?: () => void;
}

/** Every SKU in the manager DB starts at this level; the replenish dialog tops back up to it. */
const BASE_STOCK = 1000;

export const InventoryPage: React.FC<InventoryPageProps> = ({ currentUser, onOpenLogin }) => {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');

  // Manual Adjust Modal (Admin / Warehouse Manager)
  const [adjustItem, setAdjustItem] = useState<InventoryItem | null>(null);
  const [adjustDelta, setAdjustDelta] = useState(10);
  const [adjustReason, setAdjustReason] = useState('ADMIN_ADJUST');
  const [adjustLoading, setAdjustLoading] = useState(false);

  // Replenish Modal (Admin / Warehouse Manager): add units to a SKU directly
  const [replenishItem, setReplenishItem] = useState<InventoryItem | null>(null);
  const [replenishQty, setReplenishQty] = useState(100);
  const [replenishLoading, setReplenishLoading] = useState(false);

  // Quick feedback banner
  const [feedback, setFeedback] = useState<string | null>(null);

  const loadInventory = async () => {
    setLoading(true);
    try {
      const res = await inventoryApi.getAll({
        status: statusFilter || undefined,
        category: categoryFilter || undefined,
        search: search || undefined,
      });
      setItems(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInventory();

    // Listen to real-time inventory updates so stock number increases reflect live!
    const unsubInventory = realTimeService.onInventoryUpdate(() => {
      loadInventory();
    });

    return () => {
      unsubInventory();
    };
  }, [statusFilter, categoryFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    loadInventory();
  };

  const handleQuickOrder = async (sku: string) => {
    if (!currentUser && onOpenLogin) {
      onOpenLogin();
      return;
    }
    try {
      const res = await ordersApi.create(sku, 1);
      const result = res.data;
      if (result.success) {
        setFeedback(`Order placed successfully for ${sku}. Remaining stock: ${result.newQuantity}`);
      } else {
        setFeedback(`Order rejected for ${sku}: ${result.message}`);
      }
      setTimeout(() => setFeedback(null), 4000);
      loadInventory();
    } catch (e: any) {
      setFeedback(`Order failed: ${e.response?.data?.detail || e.message}`);
      setTimeout(() => setFeedback(null), 4000);
    }
  };

  const handleAdjustSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adjustItem) return;
    setAdjustLoading(true);
    try {
      await inventoryApi.adjust(adjustItem.sku, adjustDelta, adjustReason);
      setAdjustItem(null);
      setFeedback(`Stock adjusted for ${adjustItem.sku}. Delta: ${adjustDelta > 0 ? '+' : ''}${adjustDelta}`);
      setTimeout(() => setFeedback(null), 4000);
      loadInventory();
    } catch (e: any) {
      alert(`Adjustment error: ${e.response?.data?.detail || e.message}`);
    } finally {
      setAdjustLoading(false);
    }
  };

  const openReplenish = (item: InventoryItem) => {
    setReplenishItem(item);
    // default: top the SKU back up to the base level, otherwise a round 100
    setReplenishQty(item.quantity < BASE_STOCK ? BASE_STOCK - item.quantity : 100);
  };

  const handleReplenishSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!replenishItem) return;
    setReplenishLoading(true);
    try {
      await inventoryApi.replenish(replenishItem.sku, replenishQty, 'MANAGER_REPLENISH');
      setFeedback(`Replenished ${replenishItem.sku}: +${replenishQty} units (now ${replenishItem.quantity + replenishQty}).`);
      setReplenishItem(null);
      setTimeout(() => setFeedback(null), 4000);
      loadInventory();
    } catch (e: any) {
      alert(`Replenish error: ${e.response?.data?.detail || e.message}`);
    } finally {
      setReplenishLoading(false);
    }
  };

  const displayItems = items;

  const totalItems = displayItems.length;
  const lowCount = displayItems.filter((i) => i.status === 'LOW').length;
  const outCount = displayItems.filter((i) => i.status === 'OUT_OF_STOCK').length;
  const okCount = displayItems.filter((i) => i.status === 'OK').length;

  return (
    <div className="space-y-6">
      {/* Page Title & Scope */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">Manager Inventory</h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Master catalog and stock. Replenish any SKU directly; vendor supply orders are dispatched from this stock.
          </p>
        </div>
        <div className="text-xs px-3 py-1 rounded-full bg-white text-slate-700 border border-slate-200 font-medium shadow-xs">
          Manager DB &bull; {totalItems} SKUs
        </div>
      </div>

      {feedback && (
        <div className="p-3 bg-blue-50 border border-blue-200 rounded-xl text-blue-800 text-xs flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            <span className="font-medium">{feedback}</span>
          </div>
          <button onClick={() => setFeedback(null)} className="text-slate-400 hover:text-slate-700 text-xs font-bold">
            ✕
          </button>
        </div>
      )}

      {/* Modern KPI Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <span className="text-xs text-slate-500 font-medium">Total SKUs</span>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{totalItems}</div>
          <div className="text-[11px] text-slate-500 mt-1">Catalog items tracked</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <div className="flex items-center space-x-1.5 text-xs text-slate-600 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span>In Stock</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{okCount}</div>
          <div className="text-[11px] text-emerald-600 mt-1">Above reorder threshold</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <div className="flex items-center space-x-1.5 text-xs text-slate-600 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            <span>Low Stock</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{lowCount}</div>
          <div className="text-[11px] text-amber-600 mt-1">Approaching threshold</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <div className="flex items-center space-x-1.5 text-xs text-slate-600 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
            <span>Out of Stock</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{outCount}</div>
          <div className="text-[11px] text-rose-600 mt-1">Immediate restock needed</div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white border border-slate-200 rounded-xl p-3 sm:p-4 flex flex-col md:flex-row gap-3 items-center justify-between shadow-xs">
        <form onSubmit={handleSearch} className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
          <input
            type="text"
            placeholder="Search SKU or product name..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-3 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
          />
        </form>

        <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-500"
          >
            <option value="">All Categories</option>
            <option value="Produce">Produce</option>
            <option value="Dairy">Dairy</option>
            <option value="Bakery">Bakery</option>
            <option value="Beverages">Beverages</option>
            <option value="Snacks">Snacks</option>
            <option value="Pantry">Pantry</option>
            <option value="Household">Household</option>
            <option value="Personal Care">Personal Care</option>
            <option value="Electronics">Electronics</option>
            <option value="Frozen">Frozen</option>
            <option value="Meat">Meat</option>
          </select>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-500"
          >
            <option value="">All Statuses</option>
            <option value="OK">In Stock</option>
            <option value="LOW">Low Stock</option>
            <option value="OUT_OF_STOCK">Out of Stock</option>
          </select>

          <button
            onClick={loadInventory}
            className="px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 rounded-lg text-xs font-medium shadow-xs transition flex items-center space-x-1"
          >
            <RefreshCw className="w-3 h-3 text-slate-500" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Inventory Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 uppercase text-[11px] font-semibold tracking-wider">
                <th className="py-3 px-4">Product / SKU</th>
                <th className="py-3 px-4">Category</th>
                <th className="py-3 px-4">Price</th>
                <th className="py-3 px-4">Current Stock</th>
                <th className="py-3 px-4">Threshold</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-slate-400">
                    Loading inventory ledger...
                  </td>
                </tr>
              ) : displayItems.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-slate-400">
                    No products found matching filters.
                  </td>
                </tr>
              ) : (
                displayItems.map((item) => {
                  const percent = Math.min(100, Math.round((item.quantity / BASE_STOCK) * 100));
                  const barColor =
                    item.status === 'OK'
                      ? 'bg-emerald-500'
                      : item.status === 'LOW'
                      ? 'bg-amber-500'
                      : 'bg-rose-500';

                  return (
                    <tr key={item.sku} className="hover:bg-slate-50 transition">
                      <td className="py-3.5 px-4 font-mono">
                        <div className="font-semibold text-slate-900">{item.name}</div>
                        <div className="text-[11px] text-slate-400">{item.sku}</div>
                      </td>
                      <td className="py-3.5 px-4 text-slate-600">{item.category}</td>
                      <td className="py-3.5 px-4 font-mono text-slate-900 font-medium">
                        ${Number(item.price).toFixed(2)}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center space-x-2.5">
                          <span className="font-mono font-bold text-slate-900 w-12 text-right text-sm">
                            {item.quantity}
                          </span>
                          <div className="w-20 bg-slate-100 border border-slate-200 rounded-full h-1.5 overflow-hidden">
                            <div className={`h-full ${barColor}`} style={{ width: `${percent}%` }} />
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 px-4 font-mono text-slate-500 text-xs">
                        <span className="text-amber-600 font-semibold">{item.reorderThreshold}</span>
                        <span className="text-slate-400"> / </span>
                        <span>+{item.reorderQuantity}</span>
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium border ${
                            item.status === 'OK'
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                              : item.status === 'LOW'
                              ? 'bg-amber-50 text-amber-700 border-amber-200'
                              : 'bg-rose-50 text-rose-700 border-rose-200'
                          }`}
                        >
                          <span
                            className={`w-1.5 h-1.5 rounded-full ${
                              item.status === 'OK'
                                ? 'bg-emerald-500'
                                : item.status === 'LOW'
                                ? 'bg-amber-500'
                                : 'bg-rose-500'
                            }`}
                          />
                          <span>
                            {item.status === 'OK'
                              ? 'In Stock'
                              : item.status === 'LOW'
                              ? 'Low Stock'
                              : 'Out of Stock'}
                          </span>
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end space-x-1.5">
                          {(currentUser?.role === 'ADMIN' || currentUser?.role === 'WAREHOUSE_MANAGER') && (
                            <button
                              onClick={() => openReplenish(item)}
                              className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold flex items-center space-x-1 transition shadow-xs"
                              title="Add units to this SKU"
                            >
                              <PackagePlus className="w-3.5 h-3.5" />
                              <span>Replenish</span>
                            </button>
                          )}

                          <button
                            onClick={() => handleQuickOrder(item.sku)}
                            disabled={item.quantity === 0}
                            className="px-2.5 py-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-40 disabled:hover:bg-blue-600 text-white rounded-lg text-xs font-medium flex items-center space-x-1 transition shadow-xs"
                            title="Place 1-unit customer order"
                          >
                            <ShoppingCart className="w-3 h-3" />
                            <span>Order</span>
                          </button>

                          {currentUser?.role === 'ADMIN' && (
                            <button
                              onClick={() => setAdjustItem(item)}
                              className="p-1 text-slate-500 hover:text-slate-800 bg-white hover:bg-slate-50 border border-slate-300 rounded-lg shadow-xs transition"
                              title="Adjust Stock Ledger (Admin)"
                            >
                              <Sliders className="w-3.5 h-3.5" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Replenish Modal (Warehouse Manager / Admin) */}
      {replenishItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white border border-slate-200 w-full max-w-sm rounded-2xl shadow-2xl p-6 relative">
            <button
              onClick={() => setReplenishItem(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-700 p-1 rounded-lg transition"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-base font-bold text-slate-900">Replenish SKU</h3>
            <p className="text-xs text-slate-500 mt-0.5 font-mono">
              {replenishItem.sku} &bull; {replenishItem.name}
            </p>

            <form onSubmit={handleReplenishSubmit} className="mt-4 space-y-4">
              <div>
                <label className="text-xs text-slate-700 font-medium block mb-1">Units to add</label>
                <input
                  type="number"
                  min="1"
                  max="100000"
                  value={replenishQty}
                  onChange={(e) => setReplenishQty(Math.max(1, parseInt(e.target.value) || 1))}
                  className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-sm text-slate-900 font-mono focus:border-emerald-500 focus:outline-none"
                  required
                />
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {[50, 100, 500].map((n) => (
                    <button
                      key={n}
                      type="button"
                      onClick={() => setReplenishQty(n)}
                      className="px-2 py-0.5 rounded-md bg-slate-100 hover:bg-slate-200 text-slate-700 text-[11px] font-mono"
                    >
                      +{n}
                    </button>
                  ))}
                  {replenishItem.quantity < BASE_STOCK && (
                    <button
                      type="button"
                      onClick={() => setReplenishQty(BASE_STOCK - replenishItem.quantity)}
                      className="px-2 py-0.5 rounded-md bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border border-emerald-200 text-[11px]"
                    >
                      Top up to {BASE_STOCK}
                    </button>
                  )}
                </div>
                <div className="text-[11px] text-slate-500 mt-2">
                  Current: <strong className="text-slate-800">{replenishItem.quantity}</strong> &rarr; New:{' '}
                  <strong className="text-emerald-700">{replenishItem.quantity + replenishQty}</strong>
                </div>
              </div>

              <div className="flex items-center space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setReplenishItem(null)}
                  className="w-1/2 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={replenishLoading}
                  className="w-1/2 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm"
                >
                  {replenishLoading ? 'Replenishing...' : 'Replenish'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Adjust Stock Modal (Warehouse Manager / Admin) */}
      {adjustItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white border border-slate-200 w-full max-w-sm rounded-2xl shadow-2xl p-6 relative">
            <button
              onClick={() => setAdjustItem(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-700 p-1 rounded-lg transition"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-base font-bold text-slate-900">Adjust Stock Ledger (Manager)</h3>
            <p className="text-xs text-slate-500 mt-0.5 font-mono">
              {adjustItem.sku} &bull; {adjustItem.name}
            </p>

            <form onSubmit={handleAdjustSubmit} className="mt-4 space-y-4">
              <div>
                <label className="text-xs text-slate-700 font-medium block mb-1">
                  Quantity Adjustment (Positive to add, negative to deduct)
                </label>
                <input
                  type="number"
                  value={adjustDelta}
                  onChange={(e) => setAdjustDelta(parseInt(e.target.value) || 0)}
                  className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-sm text-slate-900 font-mono focus:border-blue-500 focus:outline-none"
                />
                <div className="text-[11px] text-slate-500 mt-1">
                  Current: {adjustItem.quantity} &rarr; New:{' '}
                  <span className="text-slate-900 font-semibold">{adjustItem.quantity + adjustDelta}</span>
                </div>
              </div>

              <div>
                <label className="text-xs text-slate-700 font-medium block mb-1">Audit Reason</label>
                <input
                  type="text"
                  value={adjustReason}
                  onChange={(e) => setAdjustReason(e.target.value)}
                  className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-xs text-slate-900 focus:border-blue-500 focus:outline-none"
                  required
                />
              </div>

              <div className="flex items-center space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setAdjustItem(null)}
                  className="w-1/2 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={adjustLoading}
                  className="w-1/2 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold shadow-sm"
                >
                  {adjustLoading ? 'Applying...' : 'Confirm'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
