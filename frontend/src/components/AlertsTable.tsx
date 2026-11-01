import { BellOff, ChevronLeft, ChevronRight, History, Loader2 } from 'lucide-react';
import { useState, type KeyboardEvent } from 'react';

import { errorMessage } from '../api/errors';
import { useAlerts, useCancelAlert } from '../hooks/queries';
import { formatDateTime, formatPercent, formatRupees, gapPercent } from '../lib/format';
import type { Alert, AlertStatus, Price } from '../types/api';
import { DirectionLabel, StatusPill } from './Badges';
import { ConfirmDialog } from './ConfirmDialog';
import { EmptyState } from './EmptyState';
import { useToast } from './Toasts';

type Tab = 'active' | 'history';

const PAGE_SIZE = 10;

const TABS: { id: Tab; label: string; statuses: AlertStatus[] }[] = [
  { id: 'active', label: 'Active Alerts', statuses: ['ACTIVE'] },
  { id: 'history', label: 'Triggered History', statuses: ['TRIGGERED', 'CANCELLED'] },
];

function TriggerOrGap({ alert, price }: { alert: Alert; price: Price | undefined }) {
  if (alert.status === 'TRIGGERED') {
    return <span className="font-mono">{formatRupees(alert.triggeredPrice)}</span>;
  }
  if (alert.status === 'ACTIVE' && price) {
    const gap = gapPercent(price.price, alert.targetPrice);
    return <span className={`font-mono ${gap >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>{formatPercent(gap)}</span>;
  }
  return <span className="text-slate-400">—</span>;
}

export function AlertsTable({ prices }: { prices: Map<string, Price> }) {
  const toast = useToast();
  const [tab, setTab] = useState<Tab>('active');
  const [pages, setPages] = useState<Record<Tab, number>>({ active: 0, history: 0 });
  const [confirming, setConfirming] = useState<Alert | null>(null);
  const page = pages[tab];
  const { statuses } = TABS.find((item) => item.id === tab)!;
  const alerts = useAlerts(statuses, page, PAGE_SIZE);
  const cancelAlert = useCancelAlert();

  function setPage(next: number) {
    setPages((current) => ({ ...current, [tab]: next }));
  }

  function cancel(alert: Alert) {
    setConfirming(null);
    cancelAlert.mutate(alert.id, {
      onSuccess: () => toast('success', `Cancelled the ${alert.symbol} alert.`),
      onError: (error) => toast('error', errorMessage(error)),
    });
  }

  function moveTab(event: KeyboardEvent) {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      const next = tab === 'active' ? 'history' : 'active';
      setTab(next);
      document.getElementById(`tab-${next}`)?.focus();
    }
  }

  const data = alerts.data;
  const rows = data?.content ?? [];
  // Deleting the last row of the last page must not strand the user on an empty page.
  if (data && rows.length === 0 && page > 0 && !alerts.isFetching) {
    setPage(Math.max(0, data.totalPages - 1));
  }

  const th = 'px-3 py-2.5 text-left text-xs font-medium whitespace-nowrap text-slate-500';
  const td = 'px-3 py-3 whitespace-nowrap';

  return (
    <section aria-label="Alerts" className="min-w-0 rounded-xl border border-slate-200 bg-white">
      <div role="tablist" aria-label="Alert lists" className="flex gap-1 border-b border-slate-200 px-2">
        {TABS.map((item) => (
          <button
            key={item.id}
            id={`tab-${item.id}`}
            type="button"
            role="tab"
            aria-selected={tab === item.id}
            tabIndex={tab === item.id ? 0 : -1}
            onClick={() => setTab(item.id)}
            onKeyDown={moveTab}
            className={`-mb-px border-b-2 px-3 py-3 text-sm font-medium whitespace-nowrap ${
              tab === item.id ? 'border-blue-600 text-blue-600' : 'border-transparent text-slate-500 hover:text-slate-900'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div role="tabpanel" aria-labelledby={`tab-${tab}`}>
        {alerts.isPending && (
          <div className="flex justify-center p-12">
            <Loader2 aria-label="Loading alerts" className="size-5 animate-spin text-slate-400" />
          </div>
        )}
        {alerts.isError && !data && (
          <p role="alert" className="p-6 text-sm text-rose-700">
            Could not load alerts. {errorMessage(alerts.error)}
          </p>
        )}
        {data && rows.length === 0 && page === 0 && (
          tab === 'active' ? (
            <EmptyState icon={BellOff} title="No active alerts" hint="Pick a stock from the watchlist and set a target price." />
          ) : (
            <EmptyState icon={History} title="No history yet" hint="Alerts appear here after they trigger or you cancel them." />
          )
        )}
        {rows.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50">
                <tr>
                  <th scope="col" className={th}>Symbol</th>
                  <th scope="col" className={th}>Direction</th>
                  <th scope="col" className={`${th} text-right`}>Target (₹)</th>
                  <th scope="col" className={`${th} text-right`}>{tab === 'active' ? 'Gap %' : 'Trigger price'}</th>
                  <th scope="col" className={th}>Created</th>
                  <th scope="col" className={th}>Status</th>
                  <th scope="col" className={`${th} text-right`}>Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((alert) => (
                  <tr key={alert.id} className="hover:bg-slate-50">
                    <td className={`${td} font-semibold`}>{alert.symbol}</td>
                    <td className={td}><DirectionLabel direction={alert.direction} /></td>
                    <td className={`${td} text-right font-mono`}>{formatRupees(alert.targetPrice)}</td>
                    <td className={`${td} text-right`}><TriggerOrGap alert={alert} price={prices.get(alert.symbol)} /></td>
                    <td className={`${td} text-slate-500`}>{formatDateTime(alert.createdAt)}</td>
                    <td className={td}><StatusPill status={alert.status} /></td>
                    <td className={`${td} text-right`}>
                      {alert.status === 'ACTIVE' ? (
                        <button
                          type="button"
                          aria-label={`Cancel ${alert.symbol} alert at ${formatRupees(alert.targetPrice)}`}
                          disabled={cancelAlert.isPending}
                          onClick={() => setConfirming(alert)}
                          className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                        >
                          Cancel
                        </button>
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-2.5 text-xs text-slate-500">
            <span>
              Page <span className="font-mono">{data.number + 1}</span> of <span className="font-mono">{data.totalPages}</span>
              {' · '}
              <span className="font-mono">{data.totalElements}</span> alerts
            </span>
            <span className="flex gap-1">
              <button
                type="button"
                aria-label="Previous page"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="rounded-lg border border-slate-200 p-1 hover:bg-slate-50 disabled:opacity-40"
              >
                <ChevronLeft aria-hidden className="size-4" />
              </button>
              <button
                type="button"
                aria-label="Next page"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage(page + 1)}
                className="rounded-lg border border-slate-200 p-1 hover:bg-slate-50 disabled:opacity-40"
              >
                <ChevronRight aria-hidden className="size-4" />
              </button>
            </span>
          </div>
        )}
      </div>

      {confirming && (
        <ConfirmDialog
          title="Cancel this alert?"
          message={`You won't be notified when ${confirming.symbol} crosses ${confirming.direction.toLowerCase()} ${formatRupees(confirming.targetPrice)}.`}
          confirmLabel="Cancel alert"
          onConfirm={() => cancel(confirming)}
          onCancel={() => setConfirming(null)}
        />
      )}
    </section>
  );
}
