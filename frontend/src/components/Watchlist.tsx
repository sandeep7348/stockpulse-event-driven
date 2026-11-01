import { BellPlus, Loader2, Search, SearchX } from 'lucide-react';
import { useState } from 'react';

import { useInstrumentSearch } from '../hooks/queries';
import { useDebounced } from '../hooks/useDebounced';
import { formatRupees } from '../lib/format';
import type { Instrument, Price } from '../types/api';
import { EmptyState } from './EmptyState';

const SEARCH_DELAY_MS = 250;

export function Watchlist({
  instruments,
  prices,
  loading,
  error,
  selected,
  onSetAlert,
}: {
  instruments: Instrument[];
  prices: Map<string, Price>;
  loading: boolean;
  error: boolean;
  selected: string;
  onSetAlert: (instrument: Instrument) => void;
}) {
  const [search, setSearch] = useState('');
  const term = useDebounced(search, SEARCH_DELAY_MS);
  const searching = search.trim().length > 0;
  const results = useInstrumentSearch(term);

  const visible = searching ? (term.trim() ? (results.data ?? []) : []) : instruments;
  const waiting = searching && (search !== term || results.isPending);

  return (
    <section aria-label="Watchlist" className="rounded-xl border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-3">
        <h2 className="text-sm font-semibold">{searching ? 'All NSE stocks' : 'Watchlist'}</h2>
        <div className="relative mt-2">
          <Search aria-hidden className="pointer-events-none absolute top-2.5 left-2.5 size-4 text-slate-500" />
          <input
            type="search"
            aria-label="Search all NSE stocks"
            placeholder="Search all NSE stocks"
            maxLength={40}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="w-full rounded-lg border border-slate-200 py-2 pr-3 pl-8 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
          />
        </div>
        {searching && (
          <p className="mt-2 text-xs text-slate-500">Prices appear once you pick a stock with the bell button.</p>
        )}
      </div>

      {loading && !searching && (
        <div aria-hidden className="space-y-3 p-3">
          {[1, 2, 3, 4, 5].map((row) => (
            <div key={row} className="h-9 animate-pulse rounded-lg bg-slate-100" />
          ))}
        </div>
      )}
      {error && !searching && <p className="p-4 text-sm text-rose-700">Could not load the stock list.</p>}
      {searching && results.isError && <p className="p-4 text-sm text-rose-700">Search isn't available right now.</p>}
      {waiting && visible.length === 0 && (
        <div className="flex justify-center p-8">
          <Loader2 aria-label="Searching" className="size-5 animate-spin text-slate-400" />
        </div>
      )}
      {!waiting && !loading && !results.isError && !error && visible.length === 0 && (
        <EmptyState icon={SearchX} title="No matching stocks" hint="Try a different symbol, company name or ISIN." />
      )}

      <ul className="max-h-[32rem] divide-y divide-slate-100 overflow-y-auto lg:max-h-[calc(100dvh-11.5rem)]">
        {visible.map((item) => {
          const active = item.symbol === selected;
          return (
            <li key={item.symbol} className={`flex items-center gap-2 px-3 py-2.5 ${active ? 'bg-blue-50' : ''}`}>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{item.symbol}</p>
                <p className="truncate text-xs text-slate-500" title={`${item.name} · ${item.isin}`}>
                  {item.name}
                </p>
                <p className="font-mono text-[11px] text-slate-400">{item.isin}</p>
              </div>
              <span className="font-mono text-sm font-medium whitespace-nowrap">
                {formatRupees(prices.get(item.symbol)?.price)}
              </span>
              <button
                type="button"
                aria-label={`Set alert for ${item.symbol}`}
                title="Set alert"
                onClick={() => onSetAlert(item)}
                className="rounded-lg border border-slate-200 bg-white p-1.5 text-blue-600 hover:bg-blue-50"
              >
                <BellPlus aria-hidden className="size-4" />
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
