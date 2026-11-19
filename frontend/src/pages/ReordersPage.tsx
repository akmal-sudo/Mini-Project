import React, { useState, useEffect } from 'react';
import {
  RefreshCw,
  Truck,
  CheckCircle2,
  Clock,
  Check,
  PackagePlus,
} from 'lucide-react';
import { reordersApi, vendorApi } from '../services/api';
import { realTimeService } from '../services/websocket';
import { OrderFromManagerModal } from '../components/OrderFromManagerModal';
import type { ReorderItem, User } from '../types';

interface ReordersPageProps {
  currentUser: User | null;
}

/**
 * Vendor supply orders.
 *  Vendor : orders from the manager catalog -> confirms receipt (vendor stock increases)
 *  Manager: acknowledges -> dispatches (manager stock decreases). Sees vendors only as "V<id>".
 */
export const ReordersPage: React.FC<ReordersPageProps> = ({ currentUser }) => {
  const [reorders, setReorders] = useState<ReorderItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [selectedVendorFilter, setSelectedVendorFilter] = useState<'ALL' | number>('ALL');
  const [vendorName, setVendorName] = useState<string | null>(null);

  const [feedback, setFeedback] = useState<{ message: string; type: 'success' | 'info' } | null>(null);
  const [showOrderModal, setShowOrderModal] = useState(false);

  const isVendor = currentUser?.role === 'VENDOR';
  const isManager = currentUser?.role === 'WAREHOUSE_MANAGER' || currentUser?.role === 'ADMIN';

  const showFeedback = (message: string, type: 'success' | 'info' = 'info') => {
    setFeedback({ message, type });
    setTimeout(() => setFeedback(null), 5000);
  };

  const loadReorders = async () => {
    setLoading(true);
    try {
      const res = await reordersApi.getAll();
      setReorders(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReorders();

    if (isVendor) {
      vendorApi
        .getMyCatalog()
        .then((res) => setVendorName(res.data.vendorName))
        .catch(() => setVendorName(null));
    }

    const unsubscribe = realTimeService.onReorder((item) => {
      // the live topic is broadcast to everybody: a vendor only keeps its own orders
      if (isVendor && item.vendorId !== currentUser?.vendorId) return;
      setReorders((prev) => [item, ...prev.filter((r) => r.reorderId !== item.reorderId)]);
    });

    return () => {
      unsubscribe();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isVendor, currentUser?.vendorId]);

  const handleAction = async (action: 'ack' | 'ship' | 'deliver' | 'cancel', r: ReorderItem) => {
    setActionLoading(r.reorderId);
    try {
      if (action === 'ack') {
        await reordersApi.acknowledge(r.reorderId);
        showFeedback(`Order #${r.reorderId} acknowledged. Ready to dispatch.`);
      } else if (action === 'ship') {
        await reordersApi.ship(r.reorderId);
        showFeedback(`Order #${r.reorderId} dispatched: manager stock of ${r.sku} reduced by ${r.quantity}.`, 'success');
      } else if (action === 'deliver') {
        await reordersApi.deliver(r.reorderId);
        showFeedback(`Receipt confirmed: your stock of ${r.sku} increased by +${r.quantity} units.`, 'success');
      } else {
        await reordersApi.cancel(r.reorderId, isVendor ? 'Cancelled by vendor' : 'Cancelled by manager');
        showFeedback(`Order #${r.reorderId} has been cancelled.`);
      }
      loadReorders();
    } catch (e: any) {
      alert(`Action error: ${e.response?.data?.detail || e.message}`);
    } finally {
      setActionLoading(null);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'CREATED':
        return 'bg-purple-50 text-purple-700 border-purple-200';
      case 'ACKNOWLEDGED':
        return 'bg-blue-50 text-blue-700 border-blue-200';
      case 'SHIPPED':
        return 'bg-amber-50 text-amber-700 border-amber-200';
      case 'DELIVERED':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200';
      case 'CANCELLED':
        return 'bg-rose-50 text-rose-700 border-rose-200';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  };

  const statusLabel = (status: string) => {
    switch (status) {
      case 'CREATED':
        return isManager ? 'PENDING ACK' : 'ORDER SENT';
      case 'ACKNOWLEDGED':
        return isManager ? 'READY TO DISPATCH' : 'ACKNOWLEDGED';
      case 'SHIPPED':
        return isManager ? 'DISPATCHED' : 'ON THE WAY';
      case 'DELIVERED':
        return 'RECEIVED';
      default:
        return status;
    }
  };

  // Manager filter chips come from the data itself; a vendor is only ever "V<id>" (no names)
  const vendorIds = Array.from(new Set(reorders.map((r) => r.vendorId))).sort((a, b) => a - b);

  const filteredReorders =
    isManager && selectedVendorFilter !== 'ALL'
      ? reorders.filter((r) => r.vendorId === selectedVendorFilter)
      : reorders;

  const createdList = filteredReorders.filter((r) => r.status === 'CREATED');
  const ackList = filteredReorders.filter((r) => r.status === 'ACKNOWLEDGED');
  const shippedList = filteredReorders.filter((r) => r.status === 'SHIPPED');
  const deliveredList = filteredReorders.filter((r) => r.status === 'DELIVERED');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">
            {isVendor ? 'My Supply Orders' : 'Vendor Supply Orders'}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            {isVendor
              ? 'Order products from the Store Manager (your own range or anything else in the manager catalog), track dispatch and confirm receipt.'
              : 'Acknowledge vendor orders and dispatch the units. Dispatching takes the units out of your stock - replenish the SKU first if you are short.'}
          </p>
        </div>

        <div className="flex items-center space-x-2">
          {isVendor && (
            <button
              onClick={() => setShowOrderModal(true)}
              className="flex items-center space-x-1.5 px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
            >
              <PackagePlus className="w-4 h-4" />
              <span>Order from Manager Catalog</span>
            </button>
          )}

          <button
            onClick={loadReorders}
            className="flex items-center space-x-1 px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 rounded-lg text-xs font-medium shadow-xs transition"
          >
            <RefreshCw className="w-3.5 h-3.5 text-slate-500" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {feedback && (
        <div
          className={`p-3.5 rounded-xl border text-xs flex items-center justify-between transition ${
            feedback.type === 'success'
              ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
              : 'bg-blue-50 border-blue-200 text-blue-800'
          }`}
        >
          <div className="flex items-center space-x-2">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-600" />
            <span className="font-medium">{feedback.message}</span>
          </div>
          <button onClick={() => setFeedback(null)} className="text-slate-400 hover:text-slate-700 text-xs font-bold">
            ✕
          </button>
        </div>
      )}

      {isManager ? (
        <div className="bg-white border border-slate-200 rounded-xl p-3 flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-xs">
          <span className="text-xs font-semibold text-slate-700">Filter by vendor ID:</span>
          <div className="flex flex-wrap items-center gap-1.5">
            <button
              onClick={() => setSelectedVendorFilter('ALL')}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                selectedVendorFilter === 'ALL'
                  ? 'bg-slate-100 text-slate-900 border border-slate-200 font-semibold'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              All ({reorders.length})
            </button>
            {vendorIds.map((id) => (
              <button
                key={id}
                onClick={() => setSelectedVendorFilter(id)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium font-mono transition ${
                  selectedVendorFilter === id
                    ? 'bg-blue-50 text-blue-800 border border-blue-200 font-semibold'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`}
              >
                V{id} ({reorders.filter((r) => r.vendorId === id).length})
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Truck className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>
              <strong>Supplier Portal:</strong> signed in as <strong>{vendorName ?? `Vendor #${currentUser?.vendorId}`}</strong>.
              You only see your own orders.
            </span>
          </div>
        </div>
      )}

      {/* 4 Pipeline Stages */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs font-semibold text-purple-700 flex items-center justify-between">
            <span>1. Ordered</span>
            <Clock className="w-3.5 h-3.5 text-purple-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight font-mono">{createdList.length}</div>
          <div className="text-[11px] text-slate-500 mt-1">
            {isManager ? 'Awaiting your acknowledgment' : 'Sent to Store Manager'}
          </div>
        </div>

        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs font-semibold text-blue-700 flex items-center justify-between">
            <span>2. Acknowledged</span>
            <Check className="w-3.5 h-3.5 text-blue-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight font-mono">{ackList.length}</div>
          <div className="text-[11px] text-slate-500 mt-1">
            {isManager ? 'Ready for you to dispatch' : 'Manager is preparing dispatch'}
          </div>
        </div>

        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs font-semibold text-amber-700 flex items-center justify-between">
            <span>3. Dispatched</span>
            <Truck className="w-3.5 h-3.5 text-amber-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight font-mono">{shippedList.length}</div>
          <div className="text-[11px] text-slate-500 mt-1">
            {isManager ? 'Units left your stock' : 'Confirm receipt to add to your stock'}
          </div>
        </div>

        <div className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs">
          <div className="text-xs font-semibold text-emerald-700 flex items-center justify-between">
            <span>4. Received</span>
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight font-mono">{deliveredList.length}</div>
          <div className="text-[11px] text-slate-500 mt-1">
            {isManager ? 'Completed' : 'Added to your stock'}
          </div>
        </div>
      </div>

      {/* Orders Table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 uppercase text-[11px] font-semibold tracking-wider">
                <th className="py-3 px-4">Order ID</th>
                <th className="py-3 px-4">Product SKU</th>
                {isManager && <th className="py-3 px-4">Vendor ID</th>}
                <th className="py-3 px-4">Quantity</th>
                <th className="py-3 px-4">Priority</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4">Timestamp</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={8} className="text-center py-8 text-slate-400">
                    Loading supply orders...
                  </td>
                </tr>
              ) : filteredReorders.length === 0 ? (
                <tr>
                  <td colSpan={8} className="text-center py-8 text-slate-400">
                    No supply orders yet.
                  </td>
                </tr>
              ) : (
                filteredReorders.map((r) => (
                  <tr key={r.reorderId} className="hover:bg-slate-50 transition">
                    <td className="py-3.5 px-4 font-mono font-medium text-slate-900">#{r.reorderId}</td>
                    <td className="py-3.5 px-4 font-mono font-semibold text-slate-800">{r.sku}</td>
                    {isManager && (
                      <td className="py-3.5 px-4 font-mono text-slate-700">V{r.vendorId}</td>
                    )}
                    <td className="py-3.5 px-4">
                      <span className="font-bold text-slate-900 text-sm">{r.quantity}</span>
                      <span className="text-slate-400 text-[11px] ml-1">units</span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${
                          r.severity === 'CRITICAL'
                            ? 'bg-rose-50 text-rose-700 border-rose-200'
                            : r.severity === 'HIGH'
                            ? 'bg-amber-50 text-amber-700 border-amber-200'
                            : 'bg-slate-50 text-slate-700 border-slate-200'
                        }`}
                      >
                        {r.severity}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`px-2.5 py-1 rounded-full font-mono text-[10px] font-semibold border uppercase ${getStatusBadge(
                          r.status
                        )}`}
                      >
                        {statusLabel(r.status)}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 font-mono text-slate-500 text-[11px]">
                      <div>Ordered: {new Date(r.createdAt).toLocaleString()}</div>
                      {r.deliveredAt && (
                        <div className="text-emerald-600 font-medium mt-0.5">
                          Received: {new Date(r.deliveredAt).toLocaleString()}
                        </div>
                      )}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end space-x-1.5">
                        {r.status === 'CREATED' &&
                          (isManager ? (
                            <button
                              onClick={() => handleAction('ack', r)}
                              disabled={actionLoading === r.reorderId}
                              className="px-3 py-1 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition shadow-xs flex items-center space-x-1"
                            >
                              <Check className="w-3.5 h-3.5" />
                              <span>Acknowledge</span>
                            </button>
                          ) : (
                            <span className="text-[11px] text-purple-700 bg-purple-50 border border-purple-200 px-2 py-1 rounded-lg font-medium flex items-center space-x-1">
                              <Clock className="w-3 h-3 text-purple-500" />
                              <span>Awaiting Store Manager</span>
                            </span>
                          ))}

                        {r.status === 'ACKNOWLEDGED' &&
                          (isManager ? (
                            <button
                              onClick={() => handleAction('ship', r)}
                              disabled={actionLoading === r.reorderId}
                              className="px-3 py-1 bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition shadow-xs flex items-center space-x-1"
                              title="Dispatch the units (reduces your stock)"
                            >
                              <Truck className="w-3.5 h-3.5" />
                              <span>Dispatch</span>
                            </button>
                          ) : (
                            <span className="text-[11px] text-blue-700 bg-blue-50 border border-blue-200 px-2 py-1 rounded-lg font-medium flex items-center space-x-1">
                              <Clock className="w-3 h-3 text-blue-500" />
                              <span>Manager preparing dispatch</span>
                            </span>
                          ))}

                        {r.status === 'SHIPPED' &&
                          (isVendor ? (
                            <button
                              onClick={() => handleAction('deliver', r)}
                              disabled={actionLoading === r.reorderId}
                              className="px-3 py-1 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition shadow-xs flex items-center space-x-1"
                              title="Confirm receipt - increases your stock"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Confirm Receipt</span>
                            </button>
                          ) : (
                            <span className="text-[11px] text-amber-700 bg-amber-50 border border-amber-200 px-2 py-1 rounded-lg font-medium flex items-center space-x-1">
                              <Truck className="w-3 h-3 text-amber-500" />
                              <span>Awaiting vendor receipt</span>
                            </span>
                          ))}

                        {r.status === 'DELIVERED' && (
                          <span className="text-[11px] text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-1 rounded-lg font-medium flex items-center space-x-1">
                            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                            <span>{isVendor ? `My stock +${r.quantity}` : 'Completed'}</span>
                          </span>
                        )}

                        {/* Vendors may cancel only before acknowledgment; managers until receipt */}
                        {r.status !== 'DELIVERED' &&
                          r.status !== 'CANCELLED' &&
                          (isManager || r.status === 'CREATED') && (
                            <button
                              onClick={() => handleAction('cancel', r)}
                              disabled={actionLoading === r.reorderId}
                              className="px-2 py-1 text-slate-400 hover:text-rose-600 rounded-lg text-xs font-medium transition"
                              title="Cancel order"
                            >
                              Cancel
                            </button>
                          )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {isVendor && (
        <OrderFromManagerModal
          isOpen={showOrderModal}
          onClose={() => setShowOrderModal(false)}
          onOrdered={(msg) => {
            showFeedback(msg, 'success');
            loadReorders();
          }}
        />
      )}
    </div>
  );
};
