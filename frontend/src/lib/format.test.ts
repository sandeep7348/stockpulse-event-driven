import { describe, expect, it } from 'vitest';

import {
  feedState,
  formatPercent,
  formatRupees,
  gapPercent,
  isAlreadyCrossed,
  isMarketOpen,
  targetPriceError,
} from './format';

describe('formatRupees', () => {
  it('shows API rupee values as they are, with Indian grouping', () => {
    expect(formatRupees(1397.75)).toBe('₹1,397.75');
    expect(formatRupees(123456.5)).toBe('₹1,23,456.50');
  });

  it('shows a dash when there is no price', () => {
    expect(formatRupees(null)).toBe('—');
  });
});

describe('gapPercent', () => {
  it('is signed from the current price to the target', () => {
    expect(formatPercent(gapPercent(1000, 1010))).toBe('+1.00%');
    expect(formatPercent(gapPercent(1000, 990))).toBe('-1.00%');
  });
});

describe('isAlreadyCrossed', () => {
  it('matches the backend rule, including exactly at the target', () => {
    expect(isAlreadyCrossed('ABOVE', 100, 100)).toBe(true);
    expect(isAlreadyCrossed('ABOVE', 100, 99.95)).toBe(false);
    expect(isAlreadyCrossed('BELOW', 100, 100)).toBe(true);
    expect(isAlreadyCrossed('BELOW', 100, 100.05)).toBe(false);
  });
});

describe('targetPriceError', () => {
  it.each(['1450', '1450.5', '1450.55', '0.05'])('accepts %s', (input) => {
    expect(targetPriceError(input)).toBeNull();
  });

  it.each(['', ' ', '0', '-5', '1450.555', 'abc', '1e3', '123456789'])('rejects "%s"', (input) => {
    expect(targetPriceError(input)).not.toBeNull();
  });
});

describe('isMarketOpen', () => {
  it('follows IST session hours', () => {
    expect(isMarketOpen(new Date('2026-11-02T03:44:00Z'))).toBe(false);
    expect(isMarketOpen(new Date('2026-11-02T03:45:00Z'))).toBe(true);
    expect(isMarketOpen(new Date('2026-11-02T09:59:00Z'))).toBe(true);
    expect(isMarketOpen(new Date('2026-11-02T10:00:00Z'))).toBe(false);
  });

  it('is closed at weekends', () => {
    expect(isMarketOpen(new Date('2026-10-31T05:00:00Z'))).toBe(false);
  });
});

describe('feedState', () => {
  const now = 1_000_000_000;

  it('never calls simulated prices live', () => {
    expect(feedState('simulated', true, now, now)).toBe('simulated');
  });

  it('is live only with a recent Upstox price during market hours', () => {
    expect(feedState('upstox', true, now - 10_000, now)).toBe('live');
    expect(feedState('upstox', true, now - 121_000, now)).toBe('stale');
    expect(feedState('upstox', true, null, now)).toBe('waiting');
    expect(feedState('upstox', false, now - 10_000, now)).toBe('closed');
  });

  it('allows Yahoo a longer delay before calling its prices stale', () => {
    expect(feedState('yahoo', true, now - 19 * 60_000, now)).toBe('live');
    expect(feedState('yahoo', true, now - 21 * 60_000, now)).toBe('stale');
    expect(feedState('yahoo', false, now - 21 * 60_000, now)).toBe('closed');
  });

  it('does not recognise other feeds', () => {
    expect(feedState('none', true, now, now)).toBe('unknown');
    expect(feedState(undefined, true, now, now)).toBe('unknown');
  });
});
