import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';

import { compactNumber, niceTicks, priceDomain, xTickIndices } from '../lib/chart';
import { formatRupees } from '../lib/format';
import type { ChartRange, HistoryPoint } from '../types/api';

export type ChartStyle = 'line' | 'candles';

// Validated with the data visualisation palette check, including for colour blindness.
const PRICE_COLOR = '#2563EB';
const MA20_COLOR = '#D97706';
const MA50_COLOR = '#7C3AED';
const UP_COLOR = '#059669';
const DOWN_COLOR = '#E11D48';
const GRID_COLOR = '#E2E8F0';
const AXIS_TEXT = '#64748B';
const VOLUME_COLOR = '#CBD5E1';

const MARGIN = { left: 8, right: 60 };
const PRICE_HEIGHT = 260;
const VOLUME_HEIGHT = 56;
const RSI_HEIGHT = 72;
const PANEL_GAP = 22;
const AXIS_HEIGHT = 22;
const TOTAL_HEIGHT = PRICE_HEIGHT + PANEL_GAP + VOLUME_HEIGHT + PANEL_GAP + RSI_HEIGHT + AXIS_HEIGHT;
const VOLUME_TOP = PRICE_HEIGHT + PANEL_GAP;
const RSI_TOP = VOLUME_TOP + VOLUME_HEIGHT + PANEL_GAP;
const TOOLTIP_WIDTH = 172;

const IST = 'Asia/Kolkata';
const intraday = (range: ChartRange) => range === '1D' || range === '1W';

function formatAxisDate(iso: string, range: ChartRange): string {
  const options: Intl.DateTimeFormatOptions =
    range === '1D'
      ? { hour: '2-digit', minute: '2-digit', hour12: false }
      : range === '5Y'
        ? { month: 'short', year: '2-digit' }
        : { day: 'numeric', month: 'short' };
  return new Intl.DateTimeFormat('en-IN', { timeZone: IST, ...options }).format(new Date(iso));
}

export function formatPointDate(iso: string, range: ChartRange): string {
  const options: Intl.DateTimeFormatOptions = intraday(range)
    ? { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }
    : { day: 'numeric', month: 'short', year: 'numeric' };
  return new Intl.DateTimeFormat('en-IN', { timeZone: IST, ...options }).format(new Date(iso));
}

function linePath(values: (number | null)[], x: (index: number) => number, y: (value: number) => number): string {
  let path = '';
  let drawing = false;
  values.forEach((value, index) => {
    if (value === null) {
      drawing = false;
      return;
    }
    path += `${drawing ? 'L' : 'M'}${x(index).toFixed(1)} ${y(value).toFixed(1)}`;
    drawing = true;
  });
  return path;
}

function TooltipRow({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="flex items-center gap-1.5 text-slate-500">
        {color && <span aria-hidden className="h-0.5 w-3 rounded-full" style={{ backgroundColor: color }} />}
        {label}
      </span>
      <span className="font-mono font-medium text-slate-900">{value}</span>
    </div>
  );
}

export function PriceChart({
  points,
  range,
  style,
  alertTargets,
  summary,
}: {
  points: HistoryPoint[];
  range: ChartRange;
  style: ChartStyle;
  alertTargets: number[];
  summary: string;
}) {
  const container = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  const [hovered, setHovered] = useState<number | null>(null);

  // Measured before painting, so the chart never appears at the wrong size first.
  useLayoutEffect(() => {
    const element = container.current;
    if (!element) {
      return;
    }
    const measure = () => setWidth(Math.max(280, Math.round(element.getBoundingClientRect().width)));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => setHovered(null), [points]);

  const plotWidth = width - MARGIN.left - MARGIN.right;
  const step = plotWidth / Math.max(1, points.length);
  // Points are spaced evenly so nights and weekends don't leave gaps.
  const x = (index: number) => MARGIN.left + step * (index + 0.5);

  const domain = priceDomain(points, alertTargets);
  const priceY = (value: number) => PRICE_HEIGHT - ((value - domain.min) / (domain.max - domain.min)) * PRICE_HEIGHT;
  const maxVolume = Math.max(1, ...points.map((point) => point.volume));
  const volumeY = (value: number) => VOLUME_TOP + VOLUME_HEIGHT - (value / maxVolume) * VOLUME_HEIGHT;
  const rsiY = (value: number) => RSI_TOP + RSI_HEIGHT - (value / 100) * RSI_HEIGHT;

  const priceTicks = niceTicks(domain.min, domain.max, 5);
  // Several points can share a date, such as on the 1W range, so skip repeated labels.
  const dateTicks = xTickIndices(points.length, width < 480 ? 3 : 6).filter(
    (index, position, all) =>
      position === 0 ||
      formatAxisDate(points[index].time, range) !== formatAxisDate(points[all[position - 1]].time, range),
  );
  const visibleAlerts = alertTargets.filter((target) => !domain.offChart.includes(target));
  const barWidth = Math.max(1, Math.min(12, step * 0.65));
  const lastIndex = points.length - 1;

  function pointAt(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const index = Math.floor((event.clientX - bounds.left - MARGIN.left) / step);
    setHovered(Math.min(lastIndex, Math.max(0, index)));
  }

  function moveWithKeys(event: KeyboardEvent<SVGSVGElement>) {
    const moves: Record<string, number> = {
      ArrowLeft: Math.max(0, (hovered ?? lastIndex) - 1),
      ArrowRight: Math.min(lastIndex, (hovered ?? lastIndex - 1) + 1),
      Home: 0,
      End: lastIndex,
    };
    if (event.key in moves) {
      event.preventDefault();
      setHovered(moves[event.key]);
    } else if (event.key === 'Escape') {
      setHovered(null);
    }
  }

  const point = hovered !== null ? points[hovered] : null;
  const tooltipLeft =
    hovered !== null ? Math.min(Math.max(x(hovered) + 12, 0), width - MARGIN.right - TOOLTIP_WIDTH) : 0;
  const flipped = hovered !== null && x(hovered) + 12 + TOOLTIP_WIDTH > width - MARGIN.right;

  return (
    <div ref={container} className="relative w-full">
      <svg
        role="img"
        aria-label={summary}
        tabIndex={0}
        width={width}
        height={TOTAL_HEIGHT}
        className="block touch-pan-y rounded outline-none focus-visible:ring-2 focus-visible:ring-blue-600/40"
        onPointerMove={pointAt}
        onPointerDown={pointAt}
        onPointerLeave={() => setHovered(null)}
        onKeyDown={moveWithKeys}
        onBlur={() => setHovered(null)}
      >
        {priceTicks.map((tick) => (
          <g key={tick}>
            <line x1={MARGIN.left} x2={width - MARGIN.right} y1={priceY(tick)} y2={priceY(tick)} stroke={GRID_COLOR} />
            <text x={width - MARGIN.right + 6} y={priceY(tick) + 4} fontSize={11} fill={AXIS_TEXT} className="font-mono">
              {tick.toLocaleString('en-IN')}
            </text>
          </g>
        ))}

        {visibleAlerts.map((target) => (
          <g key={target}>
            <line
              x1={MARGIN.left}
              x2={width - MARGIN.right}
              y1={priceY(target)}
              y2={priceY(target)}
              stroke="#0F172A"
              strokeWidth={1}
            />
            <text x={MARGIN.left + 4} y={priceY(target) - 4} fontSize={11} fontWeight={500} fill="#0F172A">
              Your alert {formatRupees(target)}
            </text>
          </g>
        ))}

        {style === 'candles' ? (
          points.map((item, index) => {
            const up = item.close >= item.open;
            const color = up ? UP_COLOR : DOWN_COLOR;
            const top = priceY(Math.max(item.open, item.close));
            const bottom = priceY(Math.min(item.open, item.close));
            return (
              <g key={item.time}>
                <line x1={x(index)} x2={x(index)} y1={priceY(item.high)} y2={priceY(item.low)} stroke={color} />
                {/* Hollow for up and filled for down, so direction doesn't rely on colour alone. */}
                <rect
                  x={x(index) - barWidth / 2}
                  y={top}
                  width={barWidth}
                  height={Math.max(1, bottom - top)}
                  fill={up ? '#FFFFFF' : color}
                  stroke={color}
                />
              </g>
            );
          })
        ) : (
          <path
            d={linePath(points.map((item) => item.close), x, priceY)}
            fill="none"
            stroke={PRICE_COLOR}
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        )}
        <path d={linePath(points.map((item) => item.ma20), x, priceY)} fill="none" stroke={MA20_COLOR} strokeWidth={1.5} strokeLinejoin="round" />
        <path d={linePath(points.map((item) => item.ma50), x, priceY)} fill="none" stroke={MA50_COLOR} strokeWidth={1.5} strokeLinejoin="round" />

        <text x={MARGIN.left} y={VOLUME_TOP - 6} fontSize={11} fill={AXIS_TEXT}>
          Volume
        </text>
        <line x1={MARGIN.left} x2={width - MARGIN.right} y1={VOLUME_TOP + VOLUME_HEIGHT} y2={VOLUME_TOP + VOLUME_HEIGHT} stroke={GRID_COLOR} />
        <text x={width - MARGIN.right + 6} y={VOLUME_TOP + 10} fontSize={11} fill={AXIS_TEXT} className="font-mono">
          {compactNumber(maxVolume)}
        </text>
        {points.map((item, index) => (
          <rect
            key={item.time}
            x={x(index) - barWidth / 2}
            y={volumeY(item.volume)}
            width={barWidth}
            height={VOLUME_TOP + VOLUME_HEIGHT - volumeY(item.volume)}
            fill={index === hovered ? '#64748B' : VOLUME_COLOR}
          />
        ))}

        <text x={MARGIN.left} y={RSI_TOP - 6} fontSize={11} fill={AXIS_TEXT}>
          RSI (14)
        </text>
        <rect x={MARGIN.left} y={rsiY(70)} width={plotWidth} height={rsiY(30) - rsiY(70)} fill="#F8FAFC" />
        {[30, 70].map((level) => (
          <g key={level}>
            <line x1={MARGIN.left} x2={width - MARGIN.right} y1={rsiY(level)} y2={rsiY(level)} stroke={GRID_COLOR} />
            <text x={width - MARGIN.right + 6} y={rsiY(level) + 4} fontSize={11} fill={AXIS_TEXT} className="font-mono">
              {level}
            </text>
          </g>
        ))}
        <path d={linePath(points.map((item) => item.rsi14), x, rsiY)} fill="none" stroke={PRICE_COLOR} strokeWidth={1.5} strokeLinejoin="round" />

        {dateTicks.map((index) => (
          <text
            key={index}
            x={x(index)}
            y={TOTAL_HEIGHT - 6}
            fontSize={11}
            fill={AXIS_TEXT}
            textAnchor={index === 0 ? 'start' : index === lastIndex ? 'end' : 'middle'}
          >
            {formatAxisDate(points[index].time, range)}
          </text>
        ))}

        {hovered !== null && (
          <line x1={x(hovered)} x2={x(hovered)} y1={0} y2={TOTAL_HEIGHT - AXIS_HEIGHT} stroke="#94A3B8" />
        )}
      </svg>

      {point && (
        <div
          className="pointer-events-none absolute top-2 z-10 rounded-lg border border-slate-200 bg-white p-2.5 text-xs shadow-md"
          style={{ width: TOOLTIP_WIDTH, left: flipped ? Math.max(0, x(hovered!) - 12 - TOOLTIP_WIDTH) : tooltipLeft }}
        >
          <p className="mb-1.5 font-medium text-slate-900">{formatPointDate(point.time, range)} IST</p>
          <div className="space-y-0.5">
            <TooltipRow label="Close" value={formatRupees(point.close)} color={style === 'line' ? PRICE_COLOR : undefined} />
            <TooltipRow label="Open" value={formatRupees(point.open)} />
            <TooltipRow label="High" value={formatRupees(point.high)} />
            <TooltipRow label="Low" value={formatRupees(point.low)} />
            <TooltipRow label="MA 20" value={formatRupees(point.ma20)} color={MA20_COLOR} />
            <TooltipRow label="MA 50" value={formatRupees(point.ma50)} color={MA50_COLOR} />
            <TooltipRow label="RSI" value={point.rsi14?.toFixed(1) ?? '—'} />
            <TooltipRow label="Volume" value={compactNumber(point.volume)} />
          </div>
        </div>
      )}
    </div>
  );
}

export function ChartLegend({ style }: { style: ChartStyle }) {
  const items = [
    ...(style === 'line' ? [{ label: 'Close', color: PRICE_COLOR }] : []),
    { label: '20-period average', color: MA20_COLOR },
    { label: '50-period average', color: MA50_COLOR },
  ];
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
      {style === 'candles' && (
        <>
          <span className="flex items-center gap-1.5">
            <span aria-hidden className="h-3 w-2 border" style={{ borderColor: UP_COLOR }} />
            Up (hollow)
          </span>
          <span className="flex items-center gap-1.5">
            <span aria-hidden className="h-3 w-2" style={{ backgroundColor: DOWN_COLOR }} />
            Down (filled)
          </span>
        </>
      )}
      {items.map((item) => (
        <span key={item.label} className="flex items-center gap-1.5">
          <span aria-hidden className="h-0.5 w-4 rounded-full" style={{ backgroundColor: item.color }} />
          {item.label}
        </span>
      ))}
    </div>
  );
}
