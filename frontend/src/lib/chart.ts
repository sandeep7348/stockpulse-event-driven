import type { HistoryPoint } from '../types/api';

/** Evenly spaced round numbers covering the range, such as 1,100 / 1,200 / 1,300. */
export function niceTicks(min: number, max: number, count: number): number[] {
  if (!(max > min)) {
    return [min];
  }
  const rough = (max - min) / Math.max(1, count);
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const normalized = rough / magnitude;
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude;
  const ticks: number[] = [];
  for (let tick = Math.ceil(min / step) * step; tick <= max + step * 1e-6; tick += step) {
    ticks.push(Number(tick.toFixed(6)));
  }
  return ticks;
}

/** Which points get a date label along the bottom. */
export function xTickIndices(length: number, count: number): number[] {
  if (length <= 0) {
    return [];
  }
  if (length <= count) {
    return Array.from({ length }, (_, index) => index);
  }
  const step = (length - 1) / (count - 1);
  return Array.from({ length: count }, (_, index) => Math.round(index * step));
}

export function compactNumber(value: number): string {
  return new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

export interface PeriodSummary {
  last: number;
  changePercent: number;
  high: number;
  low: number;
  rsi: number | null;
  ma20: number | null;
  ma50: number | null;
}

export function summarize(points: HistoryPoint[]): PeriodSummary | null {
  if (points.length === 0) {
    return null;
  }
  const first = points[0];
  const last = points[points.length - 1];
  return {
    last: last.close,
    changePercent: ((last.close - first.close) / first.close) * 100,
    high: Math.max(...points.map((point) => point.high)),
    low: Math.min(...points.map((point) => point.low)),
    rsi: last.rsi14,
    ma20: last.ma20,
    ma50: last.ma50,
  };
}

export type RsiZone = 'above70' | 'below30' | 'between';

export function rsiZone(rsi: number): RsiZone {
  return rsi > 70 ? 'above70' : rsi < 30 ? 'below30' : 'between';
}

/** The vertical range to draw, including alert targets only if they fit without flattening the prices. */
export function priceDomain(points: HistoryPoint[], alertTargets: number[]): { min: number; max: number; offChart: number[] } {
  const values = points.flatMap((point) => [point.high, point.low, point.ma20, point.ma50]).filter((value): value is number => value !== null);
  let min = Math.min(...values);
  let max = Math.max(...values);
  const reach = (max - min) * 0.25;
  const offChart: number[] = [];
  for (const target of alertTargets) {
    if (target < min - reach || target > max + reach) {
      offChart.push(target);
    }
  }
  const visible = alertTargets.filter((target) => !offChart.includes(target));
  min = Math.min(min, ...visible);
  max = Math.max(max, ...visible);
  const padding = Math.max((max - min) * 0.06, Math.abs(max) * 0.001, 0.01);
  return { min: min - padding, max: max + padding, offChart };
}
