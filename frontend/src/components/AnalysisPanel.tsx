import { ArrowDown, ArrowUp, LineChart, Loader2 } from 'lucide-react';
import { useState } from 'react';

import { errorMessage } from '../api/errors';
import { useHistory } from '../hooks/queries';
import { compactNumber, rsiZone, summarize, type PeriodSummary } from '../lib/chart';
import { formatDateTime, formatPercent, formatRupees } from '../lib/format';
import type { ChartRange, HistoryPoint } from '../types/api';
import { Badge, SourceBadge } from './Badges';
import { EmptyState } from './EmptyState';
import { ChartLegend, formatPointDate, PriceChart, type ChartStyle } from './PriceChart';

const RANGES: ChartRange[] = ['1D', '1W', '1M', '6M', '1Y', '5Y'];
const TABLE_ROWS = 20;

const segment = (active: boolean) =>
  `rounded-md px-2.5 py-1 text-xs font-medium ${active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`;

function Stat({ label, value, detail }: { label: string; value: React.ReactNode; detail?: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-0.5 truncate text-sm font-semibold text-slate-900">{value}</p>
      {detail && <p className="mt-0.5 truncate text-xs text-slate-500">{detail}</p>}
    </div>
  );
}

function versusAverage(summary: PeriodSummary, average: number | null, label: string) {
  if (average === null) {
    return `${label}: not enough data`;
  }
  return `${summary.last >= average ? 'Above' : 'Below'} ${label}`;
}

const RSI_TEXT = { above70: 'Above 70', below30: 'Below 30', between: 'Between 30 and 70' };

function describe(symbol: string, range: ChartRange, summary: PeriodSummary) {
  return `${symbol} price over ${range}: last ${formatRupees(summary.last)}, ${formatPercent(summary.changePercent)} over the period, high ${formatRupees(summary.high)}, low ${formatRupees(summary.low)}.`;
}

function DataTable({ points, range }: { points: HistoryPoint[]; range: ChartRange }) {
  const rows = points.slice(-TABLE_ROWS).reverse();
  const th = 'px-3 py-2 text-right font-medium whitespace-nowrap text-slate-500 first:text-left';
  const td = 'px-3 py-1.5 text-right font-mono whitespace-nowrap first:text-left first:font-sans';
  return (
    <details className="mt-3 border-t border-slate-200 pt-2">
      <summary className="cursor-pointer text-xs font-medium text-blue-600">View the latest data as a table</summary>
      <div className="mt-2 overflow-x-auto">
        <table className="w-full text-xs">
          <thead className="bg-slate-50">
            <tr>
              {['Time (IST)', 'Open', 'High', 'Low', 'Close', 'MA 20', 'MA 50', 'RSI', 'Volume'].map((heading) => (
                <th key={heading} scope="col" className={th}>
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((point) => (
              <tr key={point.time}>
                <td className={td}>{formatPointDate(point.time, range)}</td>
                <td className={td}>{formatRupees(point.open)}</td>
                <td className={td}>{formatRupees(point.high)}</td>
                <td className={td}>{formatRupees(point.low)}</td>
                <td className={td}>{formatRupees(point.close)}</td>
                <td className={td}>{formatRupees(point.ma20)}</td>
                <td className={td}>{formatRupees(point.ma50)}</td>
                <td className={td}>{point.rsi14?.toFixed(1) ?? '—'}</td>
                <td className={td}>{compactNumber(point.volume)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}

export function AnalysisPanel({ symbol, alertTargets }: { symbol: string; alertTargets: number[] }) {
  const [range, setRange] = useState<ChartRange>('6M');
  const [style, setStyle] = useState<ChartStyle>('line');
  const history = useHistory(symbol, range);
  const data = history.data;
  const points = data?.points ?? [];
  const summary = summarize(points);
  const up = (summary?.changePercent ?? 0) >= 0;
  const offChart = alertTargets.filter((target) => {
    if (!summary) {
      return false;
    }
    const reach = (summary.high - summary.low) * 0.25;
    return target < summary.low - reach || target > summary.high + reach;
  });

  return (
    <section aria-label="Price analysis" className="min-w-0 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold">{symbol ? `${symbol} analysis` : 'Analysis'}</h2>
          {data && <SourceBadge source={data.source} />}
          {data?.stale && (
            <Badge tone="amber" title="The latest update failed, so this is a saved copy">
              Saved {formatDateTime(data.fetchedAt)}
            </Badge>
          )}
          {history.isFetching && <Loader2 aria-label="Updating chart" className="size-3.5 animate-spin text-slate-400" />}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div role="group" aria-label="Chart style" className="flex gap-0.5 rounded-lg bg-slate-100 p-0.5">
            {(['line', 'candles'] as const).map((item) => (
              <button
                key={item}
                type="button"
                aria-pressed={style === item}
                onClick={() => setStyle(item)}
                className={`${segment(style === item)} capitalize`}
              >
                {item}
              </button>
            ))}
          </div>
          <div role="group" aria-label="Time range" className="flex gap-0.5 rounded-lg bg-slate-100 p-0.5">
            {RANGES.map((item) => (
              <button
                key={item}
                type="button"
                aria-pressed={range === item}
                onClick={() => setRange(item)}
                className={segment(range === item)}
              >
                {item}
              </button>
            ))}
          </div>
        </div>
      </div>

      {history.isPending && symbol && (
        <div className="flex h-96 items-center justify-center">
          <Loader2 aria-label="Loading chart" className="size-6 animate-spin text-slate-400" />
        </div>
      )}
      {history.isError && !data && (
        <EmptyState icon={LineChart} title="Chart isn't available" hint={errorMessage(history.error)} />
      )}
      {data && points.length === 0 && (
        <EmptyState icon={LineChart} title="No price history" hint="Yahoo Finance has no data for this stock and range." />
      )}

      {data && summary && (
        <div className={history.isPlaceholderData ? 'opacity-50 transition-opacity' : 'transition-opacity'}>
          <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 border-y border-slate-100 py-3 sm:grid-cols-4">
            <Stat
              label={`Change over ${data.range}`}
              value={
                <span className={`inline-flex items-center gap-0.5 ${up ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {up ? <ArrowUp aria-hidden className="size-3.5" /> : <ArrowDown aria-hidden className="size-3.5" />}
                  {formatPercent(summary.changePercent)}
                </span>
              }
              detail={`Last ${formatRupees(summary.last)}`}
            />
            <Stat
              label={`High / low over ${data.range}`}
              value={formatRupees(summary.high)}
              detail={formatRupees(summary.low)}
            />
            <Stat
              label="52-week high / low"
              value={formatRupees(data.fiftyTwoWeekHigh)}
              detail={formatRupees(data.fiftyTwoWeekLow)}
            />
            <Stat
              label="RSI (14)"
              value={summary.rsi?.toFixed(1) ?? '—'}
              detail={summary.rsi !== null ? RSI_TEXT[rsiZone(summary.rsi)] : 'Not enough data'}
            />
          </div>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
            <ChartLegend style={style} />
            <p className="text-xs text-slate-500">
              {versusAverage(summary, summary.ma20, '20-period average')} · {versusAverage(summary, summary.ma50, '50-period average')}
            </p>
          </div>

          <div className="mt-3">
            <PriceChart
              points={points}
              range={data.range}
              style={style}
              alertTargets={alertTargets}
              summary={describe(data.symbol, data.range, summary)}
            />
          </div>

          {offChart.length > 0 && (
            <p className="mt-2 text-xs text-slate-500">
              Your {offChart.length === 1 ? 'alert' : 'alerts'} at {offChart.map((target) => formatRupees(target)).join(', ')}{' '}
              {offChart.length === 1 ? 'is' : 'are'} too far from this price range to draw.
            </p>
          )}

          <DataTable points={points} range={data.range} />

          <p className="mt-3 text-xs text-slate-500">
            Prices are from Yahoo Finance's unofficial data and may be delayed. Indicators only describe past prices and
            aren't investment advice. RSI above 70 or below 30 is commonly read as stretched, but that doesn't predict what
            happens next.
          </p>
        </div>
      )}
    </section>
  );
}
