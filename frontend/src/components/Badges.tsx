import { ArrowDown, ArrowUp } from 'lucide-react';
import type { ReactNode } from 'react';

import type { FeedState } from '../lib/format';
import type { AlertStatus, Direction } from '../types/api';

const BASE = 'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset';

export const TONES = {
  emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  rose: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/25',
  blue: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/20',
} as const;

export function Badge({ tone, children, title }: { tone: keyof typeof TONES; children: ReactNode; title?: string }) {
  return (
    <span title={title} className={`${BASE} ${TONES[tone]}`}>
      {children}
    </span>
  );
}

const STATUS_TONES: Record<AlertStatus, keyof typeof TONES> = {
  ACTIVE: 'blue',
  TRIGGERED: 'emerald',
  CANCELLED: 'slate',
};

export function StatusPill({ status }: { status: AlertStatus }) {
  return <Badge tone={STATUS_TONES[status]}>{status[0] + status.slice(1).toLowerCase()}</Badge>;
}

export function DirectionLabel({ direction }: { direction: Direction }) {
  const above = direction === 'ABOVE';
  const Icon = above ? ArrowUp : ArrowDown;
  return (
    <span className={`inline-flex items-center gap-1 font-medium ${above ? 'text-emerald-600' : 'text-rose-600'}`}>
      <Icon aria-hidden className="size-3.5" />
      {above ? 'Above' : 'Below'}
    </span>
  );
}

export function SourceBadge({ source }: { source: string }) {
  if (source === 'upstox') {
    return <Badge tone="emerald">Upstox</Badge>;
  }
  return source === 'yahoo' ? <Badge tone="blue">Yahoo</Badge> : <Badge tone="amber">{source}</Badge>;
}

type FeedDisplay = { tone: keyof typeof TONES; label: string; title: string };

function feedDisplay(state: FeedState, source: string | undefined): FeedDisplay {
  if (state === 'simulated') {
    return { tone: 'amber', label: 'SIMULATED', title: 'Random prices, not real market data' };
  }
  if (state === 'unknown') {
    return { tone: 'slate', label: 'NO FEED', title: 'No price feed is configured' };
  }
  const yahoo = source === 'yahoo';
  const name = yahoo ? 'Yahoo Finance' : 'Upstox';
  const short = yahoo ? 'YAHOO' : 'UPSTOX';
  switch (state) {
    case 'live':
      return yahoo
        ? { tone: 'blue', label: 'YAHOO FINANCE', title: 'Polled from an unofficial endpoint and may be delayed' }
        : { tone: 'emerald', label: 'UPSTOX LIVE', title: 'Prices are arriving from Upstox' };
    case 'closed':
      return { tone: 'slate', label: `${short} · LAST TRADE`, title: 'Market closed: showing the last traded prices' };
    case 'stale':
      return {
        tone: 'rose',
        label: `${short} · STALE`,
        title: yahoo
          ? 'No new Yahoo Finance prices for over 20 minutes. Requests may be blocked.'
          : 'No new Upstox prices for over 2 minutes. The access token may have expired.',
      };
    case 'waiting':
      return { tone: 'amber', label: `${short} · WAITING`, title: `No prices received from ${name} yet` };
  }
}

export function FeedBadge({ state, source }: { state: FeedState; source: string | undefined }) {
  const { tone, label, title } = feedDisplay(state, source);
  return (
    <Badge tone={tone} title={title}>
      {label}
    </Badge>
  );
}
