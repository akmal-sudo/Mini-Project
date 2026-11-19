import React, { useEffect, useMemo, useState } from 'react';
import { Search, RefreshCw, Plus, Package, CheckCircle2, PackagePlus } from 'lucide-react';
import { vendorApi } from '../services/api';
import { OrderFromManagerModal } from '../components/OrderFromManagerModal';
import type { VendorCatalog } from '../types';

/**
 * Vendor's own catalog and own stock counts (vendor DB).
 * The vendor can order more of its own products, or "something else" from the manager catalog.
 */
export const VendorCatalogPage: React.FC = () => {
  const [catalog, setCatalog] = useState<VendorCatalog | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [presetSku, setPresetSku] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await vendorApi.getMyCatalog();
      setCatalog(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const items = catalog?.items ?? [];
  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return items;
    return items.filter((i) => i.sku.toLowerCase().includes(term) || i.name.toLowerCase().includes(term));
  }, [items, search]);

  const totalUnits = items.reduce((sum, i) => sum + i.quantity, 0);
  const addedCount = items.filter((i) => i.source === 'MANAGER_CATALOG').length;

  const openOrder = (sku: string | null) => {
    setPresetSku(sku);
    setModalOpen(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">My Catalog</h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Your own products and your own stock counts. Need something that is not listed here? Order it from the
            Store Manager&apos;s catalog.
          </p>
        </div>
        <div className="flex items-center space-x-2">
          <span className="text-xs px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">
            {catalog ? catalog.vendorName : 'Supplier Portal'}
          </span>
          <button
            onClick={() => openOrder(null)}
            className="flex items-center space-x-1.5 px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-xs transition"
          >
            <PackagePlus className="w-4 h-4" />
            <span>Order from Manager Catalog</span>
          </button>
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

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <span className="text-xs text-slate-500 font-medium">Products in my catalog</span>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{items.length}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <span className="text-xs text-slate-500 font-medium">Total units in my stock</span>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{totalUnits}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
          <span className="text-xs text-slate-500 font-medium">Added from manager catalog</span>
          <div className="text-2xl font-bold text-slate-900 mt-1 tracking-tight">{addedCount}</div>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-3 sm:p-4 flex flex-col md:flex-row gap-3 items-center justify-between shadow-xs">
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
          <input
            type="text"
            placeholder="Search my catalog..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-3 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-emerald-500"
          />
        </div>
        <button
          onClick={load}
          className="px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 rounded-lg text-xs font-medium shadow-xs transition flex items-center space-x-1"
        >
          <RefreshCw className="w-3 h-3 text-slate-500" />
          <span>Refresh</span>
        </button>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 uppercase text-[11px] font-semibold tracking-wider">
                <th className="py-3 px-4">Product / SKU</th>
                <th className="py-3 px-4">Category</th>
                <th className="py-3 px-4">Price</th>
                <th className="py-3 px-4">My Stock</th>
                <th className="py-3 px-4">Source</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={6} className="text-center py-8 text-slate-400">
                    Loading your catalog...
                  </td>
                </tr>
              ) : visible.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-8 text-slate-400">
                    <Package className="w-5 h-5 mx-auto mb-1 text-slate-300" />
                    No products found.
                  </td>
                </tr>
              ) : (
                visible.map((item) => (
                  <tr key={item.sku} className="hover:bg-slate-50 transition">
                    <td className="py-3.5 px-4 font-mono">
                      <div className="font-semibold text-slate-900">{item.name}</div>
                      <div className="text-[11px] text-slate-400">{item.sku}</div>
                    </td>
                    <td className="py-3.5 px-4 text-slate-600">{item.category}</td>
                    <td className="py-3.5 px-4 font-mono text-slate-900 font-medium">
                      ${Number(item.price).toFixed(2)}
                    </td>
                    <td className="py-3.5 px-4 font-mono font-bold text-slate-900 text-sm">{item.quantity}</td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`px-2 py-0.5 rounded-full text-[11px] font-medium border ${
                          item.source === 'OWN'
                            ? 'bg-slate-50 text-slate-700 border-slate-200'
                            : 'bg-emerald-50 text-emerald-700 border-emerald-200'
                        }`}
                      >
                        {item.source === 'OWN' ? 'My range' : 'From manager'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => openOrder(item.sku)}
                        className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold inline-flex items-center space-x-1 transition shadow-xs"
                        title="Order more of this product from the Store Manager"
                      >
                        <Plus className="w-3.5 h-3.5" />
                        <span>Order more</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <OrderFromManagerModal
        isOpen={modalOpen}
        initialSku={presetSku}
        onClose={() => setModalOpen(false)}
        onOrdered={(msg) => {
          setFeedback(msg);
          setTimeout(() => setFeedback(null), 6000);
          load();
        }}
      />
    </div>
  );
};
