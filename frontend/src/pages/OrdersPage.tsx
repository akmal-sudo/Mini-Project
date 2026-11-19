import React, { useState, useEffect } from 'react';
import { ShoppingCart, Plus, RefreshCw } from 'lucide-react';
import { ordersApi, inventoryApi } from '../services/api';
import type { OrderItem, InventoryItem } from '../types';

export const OrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<OrderItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');

  // Place Order Modal
  const [showOrderModal, setShowOrderModal] = useState(false);
  const [catalog, setCatalog] = useState<InventoryItem[]>([]);
  const [selectedSku, setSelectedSku] = useState('');
  const [orderQty, setOrderQty] = useState(1);
  const [orderSubmitting, setOrderSubmitting] = useState(false);
  const [orderFeedback, setOrderFeedback] = useState<string | null>(null);

  const loadOrders = async () => {
    setLoading(true);
    try {
      const res = await ordersApi.getAll({ status: statusFilter || undefined, size: 50 });
      setOrders(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
  }, [statusFilter]);

  const openOrderModal = async () => {
    try {
      const res = await inventoryApi.getAll();
      setCatalog(res.data);
      if (res.data.length > 0) {
        setSelectedSku(res.data[0].sku);
      }
      setShowOrderModal(true);
    } catch (e) {
      console.error(e);
    }
  };

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    setOrderSubmitting(true);
    setOrderFeedback(null);
    try {
      const res = await ordersApi.create(selectedSku, orderQty);
      const data = res.data;
      if (data.success) {
        setOrderFeedback(`Order placed successfully! (Event: ${data.eventId})`);
      } else {
        setOrderFeedback(`Order rejected: ${data.message}`);
      }
      loadOrders();
      setTimeout(() => setShowOrderModal(false), 1500);
    } catch (err: any) {
      setOrderFeedback(`Order failed: ${err.response?.data?.detail || err.message}`);
    } finally {
      setOrderSubmitting(false);
    }
  };

  const completedCount = orders.filter((o) => o.status === 'COMPLETED').length;
  const rejectedCount = orders.filter((o) => o.status === 'REJECTED').length;
  const failedCount = orders.filter((o) => o.status === 'FAILED').length;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">Order Stream & History</h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Real-time multi-threaded order execution with thread-safe stock deduction and ledger logging
          </p>
        </div>
        <button
          onClick={openOrderModal}
          className="flex items-center space-x-2 px-3.5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
        >
          <Plus className="w-4 h-4" />
          <span>New Order</span>
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-slate-500 font-medium">Recent Loaded</div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{orders.length}</div>
        </div>
        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-emerald-700 font-medium flex items-center space-x-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span>Completed</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{completedCount}</div>
        </div>
        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-amber-700 font-medium flex items-center space-x-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            <span>Rejected (Stockout)</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{rejectedCount}</div>
        </div>
        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs text-rose-700 font-medium flex items-center space-x-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
            <span>Failed (Constraint)</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{failedCount}</div>
        </div>
      </div>

      {/* Filter */}
      <div className="bg-white border border-slate-200 p-3 rounded-xl flex items-center justify-between shadow-xs">
        <div className="flex items-center space-x-2">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-700 font-mono focus:outline-none focus:border-blue-500"
          >
            <option value="">All Statuses</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="REJECTED">REJECTED</option>
            <option value="FAILED">FAILED</option>
          </select>
        </div>

        <button
          onClick={loadOrders}
          className="flex items-center space-x-1 px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 rounded-lg text-xs font-medium shadow-xs transition"
        >
          <RefreshCw className="w-3.5 h-3.5 text-slate-500" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 font-mono uppercase text-[10px] tracking-wider font-semibold">
                <th className="py-3 px-4">Order ID / Event ID</th>
                <th className="py-3 px-4">Target SKU</th>
                <th className="py-3 px-4">Quantity</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Processed By</th>
                <th className="py-3 px-4">Timestamp</th>
                <th className="py-3 px-4">Reason / Failure Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500 font-mono">
                    Loading order stream...
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500 font-mono">
                    No orders recorded yet.
                  </td>
                </tr>
              ) : (
                orders.map((o) => (
                  <tr key={o.orderId} className="hover:bg-slate-50/80 transition">
                    <td className="py-3 px-4 font-mono">
                      <div className="text-slate-900 font-bold">#{o.orderId}</div>
                      <div className="text-[10px] text-slate-500 truncate max-w-[140px]">{o.eventId}</div>
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-800 font-medium">
                      {o.sku}
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-900 font-semibold">
                      {o.quantity}
                    </td>
                    <td className="py-3 px-4">
                      {o.status === 'COMPLETED' ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                          Completed
                        </span>
                      ) : o.status === 'REJECTED' ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                          Rejected
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-rose-50 text-rose-700 border border-rose-200">
                          <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                          Failed
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-600 text-[11px]">
                      {o.processedBy || 'system'}
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-500 text-[11px]">
                      {new Date(o.createdAt).toLocaleTimeString()}
                    </td>
                    <td className="py-3 px-4 text-slate-600 text-[11px]">
                      {o.failureReason || <span className="text-slate-400">—</span>}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Place Order Modal */}
      {showOrderModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white border border-slate-200 w-full max-w-sm rounded-xl shadow-2xl p-6">
            <h3 className="text-base font-bold text-slate-900 flex items-center space-x-2">
              <ShoppingCart className="w-4 h-4 text-blue-600" />
              <span>Create Customer Order</span>
            </h3>
            <p className="text-xs text-slate-500 mt-1">Dispatched to the OS Concurrency Pipeline</p>

            {orderFeedback && (
              <div className="mt-3 p-2.5 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-800">
                {orderFeedback}
              </div>
            )}

            <form onSubmit={handlePlaceOrder} className="mt-4 space-y-4">
              <div>
                <label className="text-xs text-slate-700 font-medium block mb-1">Select Product</label>
                <select
                  value={selectedSku}
                  onChange={(e) => setSelectedSku(e.target.value)}
                  className="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-xs text-slate-800 font-mono focus:outline-none focus:border-blue-500"
                >
                  {catalog.map((cat) => (
                    <option key={cat.sku} value={cat.sku}>
                      {cat.sku} - {cat.name} (Stock: {cat.quantity})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-xs text-slate-700 font-medium block mb-1">Quantity</label>
                <input
                  type="number"
                  min="1"
                  value={orderQty}
                  onChange={(e) => setOrderQty(Math.max(1, parseInt(e.target.value) || 1))}
                  className="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-sm text-slate-800 font-mono focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="flex items-center space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowOrderModal(false)}
                  className="w-1/2 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 rounded-lg text-xs font-semibold transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={orderSubmitting}
                  className="w-1/2 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
                >
                  {orderSubmitting ? 'Submitting...' : 'Dispatch Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
