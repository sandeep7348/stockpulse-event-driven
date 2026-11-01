import type { Direction } from '../types/api';

const IST = 'Asia/Kolkata';

const rupees = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const dateTime = new Intl.DateTimeFormat('en-IN', {
  timeZone: IST,
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const clock = new Intl.DateTimeFormat('en-IN', {
  timeZone: IST,
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

export function formatRupees(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `₹${rupees.format(value)}`;
}

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? `${dateTime.format(new Date(iso))} IST` : '—';
}

export function formatClock(date: Date): string {
  return clock.format(date);
}

/** Percentage move needed from the current price to reach the target. */
export function gapPercent(current: number, target: number): number {
  return ((target - current) / current) * 100;
}

export function formatPercent(value: number): string {
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

export function isAlreadyCrossed(direction: Direction, target: number, current: number): boolean {
  return direction === 'ABOVE' ? current >= target : current <= target;
}

/** The backend accepts positive prices with at most two decimals and eight whole digits. */
export function targetPriceError(input: string): string | null {
  if (!input.trim()) {
    return 'Enter a target price.';
  }
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(input.trim())) {
    return 'Use a price like 1450.50, with at most two decimals.';
  }
  return Number(input) > 0 ? null : 'Target price must be above zero.';
}

/** Client-side copy of the normal NSE session, used only by demo mode. */
export function isMarketOpen(date: Date): boolean {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: IST,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date);
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? '';
  if (part('weekday') === 'Sat' || part('weekday') === 'Sun') {
    return false;
  }
  const minutes = Number(part('hour')) * 60 + Number(part('minute'));
  return minutes >= 9 * 60 + 15 && minutes < 15 * 60 + 30;
}

export type FeedState = 'simulated' | 'live' | 'closed' | 'stale' | 'waiting' | 'unknown';

// Yahoo's delay is not documented, so its prices get far longer before counting as stale.
const STALE_AFTER_MS: Record<string, number> = { upstox: 2 * 60_000, yahoo: 20 * 60_000 };

/**
 * "Live" has to be earned: the backend reports only which feed is configured, and an
 * expired token or a blocked request leaves that setting unchanged while prices stop updating.
 */
export function feedState(
  source: string | undefined,
  marketOpen: boolean | undefined,
  latestPriceAt: number | null,
  now: number,
): FeedState {
  if (source === 'simulated') {
    return 'simulated';
  }
  if (!source || !(source in STALE_AFTER_MS)) {
    return 'unknown';
  }
  if (!marketOpen) {
    return 'closed';
  }
  if (latestPriceAt === null) {
    return 'waiting';
  }
  return now - latestPriceAt > STALE_AFTER_MS[source] ? 'stale' : 'live';
}
