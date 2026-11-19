import React, { useEffect, useMemo, useState } from 'react';
import { X, Send, Search } from 'lucide-react';
import { catalogApi, vendorApi, reordersApi } from '../services/api';
import type { CatalogProduct } from '../types';

interface OrderFromManagerModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** called after an order was placed, with a human message */
  onOrdered: (message: string) => void;
  /** optional: open with this SKU preselected (e.g. "Order more" on a catalog row) */
  initialSku?: string | null;
}

type Scope = 'MINE' | 'OTHER';

/**
 * A vendor orders stock from the MANAGER catalog. Two scopes:
 *  - MINE : products already in the vendor's own catalog
 *  - OTHER: everything else the manager carries (the vendor can start supplying it)
 */
export const OrderFromManagerModal: React.FC<OrderFromManagerModalProps> = ({
  isOpen,
  onClose,
  onOrdered,
  initialSku,
}) => {
  const [catalog, setCatalog] = useState<CatalogProduct[]>([]);
  const [myStock, setMyStock] = useState<Record<string, number>>({});
  const [scope, setScope] = useState<Scope>('OTHER');
  const [search, setSearch] = useState('');
  const [selectedSku, setSelectedSku] = useState<string>('');
  const [quantity, setQuantity] = useState(50);
  const [severity, setSeverity] = useState('MEDIUM');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    setSearch('');
    setLoading(true);
    Promise.all([catalogApi.getManagerCatalog(), vendorApi.getMyCatalog()])
      .then(([cat, mine]) => {
        setCatalog(cat.data);
        const stock: Record<string, number> = {};
        mine.data.items.forEach((i) => {
          stock[i.sku] = i.quantity;
        });
        setMyStock(stock);

        const preset = initialSku ? cat.data.find((c) => c.sku === initialSku) : undefined;
        if (preset) {
          setScope(preset.inMyCatalog ? 'MINE' : 'OTHER');
          setSelectedSku(preset.sku);
        } else {
          setScope('OTHER');
          setSelectedSku('');
        }
      })
      .catch((e) => setError(e?.response?.data?.detail || e.message))
      .finally(() => setLoading(false));
  }, [isOpen, initialSku]);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    return catalog.filter((c) => {
      if (scope === 'MINE' ? !c.inMyCatalog : c.inMyCatalog) return false;
      if (!term) return true;
      return c.sku.toLowerCase().includes(term) || c.name.toLowerCase().includes(term);
    });
  }, [catalog, scope, search]);

  const otherCount = catalog.filter((c) => !c.inMyCatalog).length;
  const mineCount = catalog.length - otherCount;
  const selected = catalog.find((c) => c.sku === selectedSku);

  if (!isOpen) return null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected) {
      setError('Pick a product first.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await reordersApi.create(selected.sku, quantity, severity);
      onOrdered(
        `Order sent to the Store Manager: ${quantity} x ${selected.sku} (${selected.name}). ` +
          (selected.inMyCatalog
            ? 'Your stock rises when you confirm receipt.'
            : 'On receipt it is added to your catalog.')
      );
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.detail || err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const tabClass = (active: boolean) =>
    `flex-1 px-3 py-2 rounded-lg text-xs font-semibold transition border ${
      active
        ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
        : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
    }`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
      <div className="bg-white border border-slate-200 w-full max-w-lg rounded-2xl shadow-2xl p-6 relative max-h-[90vh] flex flex-col">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-700 p-1 rounded-lg transition"
          aria-label="Close"
        >
          <X className="w-5 h-5" />
        </button>

        <h3 className="text-base font-bold text-slate-900">Order from Manager Catalog</h3>
        <p className="text-xs text-slate-500 mt-0.5">
          Order units of any product the Store Manager carries &ndash; including products outside your own catalog.
        </p>

        <form onSubmit={submit} className="mt-4 flex flex-col min-h-0 space-y-3">
          <div className="flex gap-2">
            <button type="button" onClick={() => setScope('MINE')} className={tabClass(scope === 'MINE')}>
              My catalog ({mineCount})
            </button>
            <button type="button" onClick={() => setScope('OTHER')} className={tabClass(scope === 'OTHER')}>
              Something else ({otherCount})
            </button>
          </div>

          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search SKU or product name..."
              className="w-full bg-white border border-slate-300 rounded-lg pl-9 pr-3 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div className="border border-slate-200 rounded-xl overflow-y-auto h-48 divide-y divide-slate-100">
            {loading ? (
              <div className="p-4 text-xs text-slate-400 text-center">Loading manager catalog...</div>
            ) : visible.length === 0 ? (
              <div className="p-4 text-xs text-slate-400 text-center">No products in this list.</div>
            ) : (
              visible.map((c) => (
                <label
                  key={c.sku}
                  className={`flex items-center gap-3 px-3 py-2 cursor-pointer text-xs transition ${
                    selectedSku === c.sku ? 'bg-emerald-50' : 'hover:bg-slate-50'
                  }`}
                >
                  <input
                    type="radio"
                    name="order-sku"
                    checked={selectedSku === c.sku}
                    onChange={() => setSelectedSku(c.sku)}
                    className="accent-emerald-600"
                  />
                  <span className="flex-1 min-w-0">
                    <span className="block font-semibold text-slate-900 truncate">{c.name}</span>
                    <span className="block font-mono text-[11px] text-slate-400">
                      {c.sku} &bull; {c.category}
                    </span>
                  </span>
                  <span className="text-right shrink-0">
                    <span className="block font-mono text-slate-800">${Number(c.price).toFixed(2)}</span>
                    {c.inMyCatalog && (
                      <span className="block text-[10px] text-slate-500">you have {myStock[c.sku] ?? 0}</span>
                    )}
                  </span>
                </label>
              ))
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-medium text-slate-700 block mb-1">Quantity (units)</label>
              <input
                type="number"
                min={1}
                max={10000}
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-xs text-slate-900 font-mono focus:outline-none focus:border-emerald-500"
                required
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-700 block mb-1">Priority</label>
              <select
                value={severity}
                onChange={(e) => setSeverity(e.target.value)}
                className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-xs text-slate-900 focus:outline-none focus:border-emerald-500"
              >
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
              </select>
            </div>
          </div>

          {selected && (
            <div className="text-[11px] text-slate-500">
              {selected.inMyCatalog ? (
                <>
                  Your stock of {selected.sku}: <strong className="text-slate-800">{myStock[selected.sku] ?? 0}</strong>{' '}
                  &rarr; after receipt{' '}
                  <strong className="text-emerald-700">{(myStock[selected.sku] ?? 0) + quantity}</strong>
                </>
              ) : (
                <>
                  <strong className="text-slate-800">{selected.sku}</strong> is not in your catalog yet &ndash; it will be
                  added with <strong className="text-emerald-700">{quantity}</strong> units once you confirm receipt.
                </>
              )}
            </div>
          )}

          {error && (
            <div className="p-2.5 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-700">{error}</div>
          )}

          <div className="flex items-center justify-end space-x-2 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-xl transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !selected}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-xs transition flex items-center space-x-1"
            >
              <Send className="w-3.5 h-3.5" />
              <span>{submitting ? 'Sending...' : 'Send Order to Manager'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
