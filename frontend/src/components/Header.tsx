import { Activity, Bell, ChevronDown, Clock, LogOut } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { useAuth } from '../auth/AuthContext';
import { useNow } from '../hooks/useNow';
import { formatClock, type FeedState } from '../lib/format';
import { formatRemaining } from '../lib/session';
import type { MarketStatus } from '../types/api';
import { Badge, FeedBadge } from './Badges';

const EXPIRY_WARNING_MS = 10 * 60_000;

function MarketPill({ status }: { status: MarketStatus | undefined }) {
  if (!status) {
    return <Badge tone="slate">Checking market…</Badge>;
  }
  if (status.open) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium whitespace-nowrap text-emerald-700 ring-1 ring-emerald-600/20 ring-inset">
        <span className="relative flex size-2">
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-emerald-500 opacity-75" />
          <span className="relative inline-flex size-2 rounded-full bg-emerald-600" />
        </span>
        Market open
      </span>
    );
  }
  return <Badge tone="slate">Market closed</Badge>;
}

function UserMenu() {
  const { session, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const now = useNow(30_000);
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const closeOutside = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  if (!session) {
    return null;
  }
  const remaining = session.expiresAt - now.getTime();
  const expiring = remaining < EXPIRY_WARNING_MS;

  return (
    <div ref={container} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label="Account menu"
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1 rounded-lg p-1 hover:bg-slate-100"
      >
        <span className="grid size-7 place-items-center rounded-full bg-blue-600 text-xs font-semibold text-white uppercase">
          {session.email.charAt(0)}
        </span>
        {expiring && <span aria-hidden className="size-1.5 rounded-full bg-amber-500" />}
        <ChevronDown aria-hidden className="size-4 text-slate-500" />
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-30 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
          <div className="px-2 py-1.5">
            <p className="truncate text-sm font-medium" title={session.email}>
              {session.email}
            </p>
            <p className={`mt-1 text-xs ${expiring ? 'font-medium text-amber-700' : 'text-slate-500'}`}>
              Session expires in {formatRemaining(remaining)}
            </p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => logout()}
            className="mt-1 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-slate-100"
          >
            <LogOut aria-hidden className="size-4 text-slate-500" />
            Log out
          </button>
        </div>
      )}
    </div>
  );
}

export function Header({
  status,
  feed,
  unread,
  onOpenNotifications,
}: {
  status: MarketStatus | undefined;
  feed: FeedState;
  unread: number;
  onOpenNotifications: () => void;
}) {
  const now = useNow(1000);

  return (
    <header className="sticky top-0 z-20 border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-[1500px] flex-wrap items-center gap-x-3 gap-y-2 px-4 py-2.5">
        <div className="flex items-center gap-2">
          <span className="grid size-8 place-items-center rounded-lg bg-blue-600">
            <Activity aria-hidden className="size-4.5 text-white" />
          </span>
          <span className="font-semibold tracking-tight whitespace-nowrap">NSE AlertPulse</span>
        </div>

        <div className="order-3 flex w-full flex-wrap items-center gap-2 sm:order-none sm:ml-3 sm:w-auto">
          <MarketPill status={status} />
          <FeedBadge state={feed} source={status?.priceSource} />
          <span className="inline-flex items-center gap-1 text-xs whitespace-nowrap text-slate-500">
            <Clock aria-hidden className="size-3.5" />
            <span className="font-mono">{formatClock(now)}</span> IST
            <span className="hidden xl:inline">· Mon–Fri, 09:15–15:30</span>
          </span>
        </div>

        <div className="ml-auto flex items-center gap-1">
          <button
            type="button"
            aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
            onClick={onOpenNotifications}
            className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"
          >
            <Bell aria-hidden className="size-5" />
            {unread > 0 && (
              <span className="absolute top-0.5 right-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-rose-600 px-1 font-mono text-[10px] font-semibold text-white">
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </button>
          <UserMenu />
        </div>
      </div>
    </header>
  );
}
