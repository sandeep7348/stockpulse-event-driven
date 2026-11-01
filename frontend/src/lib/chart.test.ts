import { describe, expect, it } from 'vitest';

import type { HistoryPoint } from '../types/api';
import { niceTicks, priceDomain, rsiZone, summarize, xTickIndices } from './chart';

function point(close: number, overrides: Partial<HistoryPoint> = {}): HistoryPoint {
  return { time: '2026-10-30T09:58:00Z', open: close, high: close, low: close, close, volume: 1, ma20: null, ma50: null, rsi14: null, ...overrides };
}

describe('niceTicks', () => {
  it('uses round steps inside the range', () => {
    expect(niceTicks(1152, 1612, 4)).toEqual([1200, 1400, 1600]);
    expect(niceTicks(10.2, 10.9, 4)).toEqual([10.2, 10.4, 10.6, 10.8]);
  });

  it('copes with a flat range', () => {
    expect(niceTicks(100, 100, 4)).toEqual([100]);
  });
});

describe('xTickIndices', () => {
  it('always includes both ends', () => {
    expect(xTickIndices(101, 5)).toEqual([0, 25, 50, 75, 100]);
  });

  it('handles fewer points than ticks', () => {
    expect(xTickIndices(2, 5)).toEqual([0, 1]);
    expect(xTickIndices(0, 5)).toEqual([]);
  });
});

describe('summarize', () => {
  it('measures change from the first close to the last', () => {
    const summary = summarize([point(100, { high: 120, low: 90 }), point(110, { rsi14: 55, ma20: 105, ma50: 101 })]);
    expect(summary?.changePercent).toBeCloseTo(10);
    expect(summary).toMatchObject({ last: 110, high: 120, low: 90, rsi: 55, ma20: 105, ma50: 101 });
  });

  it('returns nothing without data', () => {
    expect(summarize([])).toBeNull();
  });
});

describe('rsiZone', () => {
  it('splits at the common 30 and 70 levels', () => {
    expect(rsiZone(70.1)).toBe('above70');
    expect(rsiZone(70)).toBe('between');
    expect(rsiZone(30)).toBe('between');
    expect(rsiZone(29.9)).toBe('below30');
  });
});

describe('priceDomain', () => {
  const points = [point(100, { high: 110, low: 90 })];

  it('includes nearby alert targets', () => {
    const domain = priceDomain(points, [112]);
    expect(domain.max).toBeGreaterThan(112);
    expect(domain.offChart).toEqual([]);
  });

  it('leaves far targets off the chart rather than flattening prices', () => {
    const domain = priceDomain(points, [500]);
    expect(domain.max).toBeLessThan(120);
    expect(domain.offChart).toEqual([500]);
  });

  it('ignores gaps in the moving averages and gives flat prices some height', () => {
    const domain = priceDomain([point(100)], []);
    expect(domain.max).toBeGreaterThan(domain.min);
  });
});
