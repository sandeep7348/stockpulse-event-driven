import { FlaskConical, WifiOff } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';

import { isDemoMode } from '../api';
import { useAcknowledged } from '../hooks/useAcknowledged';
import { useNow } from '../hooks/useNow';
import { useAlerts, useInstruments, useMarketStatus, useNotifications, usePrices } from '../hooks/queries';
import { feedState } from '../lib/format';
import type { AlertStatus, Instrument } from '../types/api';
import { AlertForm } from './AlertForm';
import { AlertsTable } from './AlertsTable';
import { AnalysisPanel } from './AnalysisPanel';
import { Header } from './Header';
import { NotificationTray } from './NotificationTray';
import { ScorecardPanel } from './ScorecardPanel';
import { useToast } from './Toasts';
import { Watchlist } from './Watchlist';

const ACTIVE: AlertStatus[] = ['ACTIVE'];
const MAX_PAGE_SIZE = 100;

export function Dashboard({ userId }: { userId: string }) {
  const toast = useToast();
  const status = useMarketStatus();
  const instruments = useInstruments();
  const defaults = useMemo(() => instruments.data ?? [], [instruments.data]);
  const notifications = useNotifications();
  const activeAlerts = useAlerts(ACTIVE, 0, MAX_PAGE_SIZE);
  const { acknowledged, acknowledge } = useAcknowledged(userId);
  const now = useNow(15_000);

  const [picked, setPicked] = useState<Instrument[]>([]);
  const [chosenSymbol, setChosenSymbol] = useState<string | null>(null);
  const [focusSignal, setFocusSignal] = useState(0);
  const [trayOpen, setTrayOpen] = useState(false);

  // Stocks picked from search join the list for this visit, on top of the defaults.
  const list = useMemo(
    () => [...picked, ...defaults.filter((item) => !picked.some((other) => other.symbol === item.symbol))],
    [picked, defaults],
  );
  const symbol = chosenSymbol ?? defaults[0]?.symbol ?? '';

  // Only stocks on screen are priced: searching thousands of stocks fetches no prices.
  const priced = useMemo(() => {
    const alerted = activeAlerts.data?.content.map((alert) => alert.symbol) ?? [];
    return [...new Set([...list.map((item) => item.symbol), ...alerted])];
  }, [list, activeAlerts.data]);
  const prices = usePrices(priced);

  const items = useMemo(() => notifications.data?.content ?? [], [notifications.data]);
  const unread = items.filter((item) => !acknowledged.has(item.id)).length;

  // Announce only notifications that arrive while the page is open, not the existing history.
  const seen = useRef<Set<string> | null>(null);
  useEffect(() => {
    if (!notifications.data) {
      return;
    }
    if (seen.current) {
      items.filter((item) => !seen.current!.has(item.id)).forEach((item) => toast('info', item.message));
    }
    seen.current = new Set(items.map((item) => item.id));
  }, [notifications.data, items, toast]);

  const latestPriceAt = useMemo(() => {
    const times = [...prices.values()].map((price) => Date.parse(price.timestamp));
    return times.length > 0 ? Math.max(...times) : null;
  }, [prices]);

  const feed = feedState(status.data?.priceSource, status.data?.open, latestPriceAt, now.getTime());

  return (
    <div className="min-h-dvh bg-slate-50">
      <Header status={status.data} feed={feed} unread={unread} onOpenNotifications={() => setTrayOpen(true)} />
      {status.isError && (
        <div role="alert" className="border-b border-rose-200 bg-rose-50">
          <p className="mx-auto flex max-w-[1500px] items-center gap-2 px-4 py-1.5 text-xs font-medium text-rose-700">
            <WifiOff aria-hidden className="size-3.5 shrink-0" />
            Can't reach the server. Prices and alerts on this page may be out of date.
          </p>
        </div>
      )}
      {isDemoMode && (
        <div className="border-b border-blue-200 bg-blue-50">
          <p className="mx-auto flex max-w-[1500px] items-center gap-2 px-4 py-1.5 text-xs text-blue-700">
            <FlaskConical aria-hidden className="size-3.5 shrink-0" />
            Demo data: sample prices and alerts running in your browser, with no backend connected.
          </p>
        </div>
      )}
      <main className="mx-auto grid max-w-[1500px] gap-4 p-4 lg:grid-cols-[20rem_minmax(0,1fr)] min-[1400px]:grid-cols-[19rem_minmax(0,1fr)_19rem]">
        <div className="order-2 lg:order-none lg:row-span-2">
          <Watchlist
            instruments={list}
            prices={prices}
            loading={instruments.isPending}
            error={instruments.isError}
            selected={symbol}
            onSetAlert={(next) => {
              if (!defaults.some((item) => item.symbol === next.symbol)) {
                setPicked((current) =>
                  current.some((item) => item.symbol === next.symbol) ? current : [next, ...current],
                );
              }
              setChosenSymbol(next.symbol);
              setFocusSignal((value) => value + 1);
            }}
          />
        </div>
        <div className="order-1 lg:order-none min-[1400px]:col-start-3 min-[1400px]:row-start-1">
          <AlertForm
            instruments={list}
            prices={prices}
            symbol={symbol}
            focusSignal={focusSignal}
            onSymbolChange={setChosenSymbol}
          />
        </div>
        <div className="order-3 min-w-0 lg:order-none min-[1400px]:col-start-2 min-[1400px]:row-start-1">
          <div className="space-y-4">
            <AnalysisPanel
              symbol={symbol}
              alertTargets={(activeAlerts.data?.content ?? [])
                .filter((alert) => alert.symbol === symbol)
                .map((alert) => alert.targetPrice)}
            />
            <ScorecardPanel symbol={symbol} />
            <AlertsTable prices={prices} />
          </div>
        </div>
      </main>
      {trayOpen && (
        <NotificationTray
          notifications={items}
          acknowledged={acknowledged}
          onAcknowledge={acknowledge}
          onClose={() => setTrayOpen(false)}
        />
      )}
    </div>
  );
}
