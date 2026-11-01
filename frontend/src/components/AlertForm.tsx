import { Loader2 } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';

import { errorMessage } from '../api/errors';
import { useCreateAlert } from '../hooks/queries';
import { formatPercent, formatRupees, gapPercent, isAlreadyCrossed, targetPriceError } from '../lib/format';
import type { Direction, Instrument, Price } from '../types/api';
import { Badge, SourceBadge } from './Badges';
import { useToast } from './Toasts';

export function AlertForm({
  instruments,
  prices,
  symbol,
  focusSignal,
  onSymbolChange,
}: {
  instruments: Instrument[];
  prices: Map<string, Price>;
  symbol: string;
  /** Changes when "Set alert" is clicked in the watchlist, moving focus to the target input. */
  focusSignal: number;
  onSymbolChange: (symbol: string) => void;
}) {
  const toast = useToast();
  const createAlert = useCreateAlert();
  const [direction, setDirection] = useState<Direction>('ABOVE');
  const [target, setTarget] = useState('');
  const [touched, setTouched] = useState(false);
  const targetInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (focusSignal > 0) {
      targetInput.current?.focus();
      targetInput.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  }, [focusSignal]);

  const price = prices.get(symbol);
  const inputError = targetPriceError(target);
  const targetValue = inputError ? null : Number(target);
  const gap = price && targetValue !== null ? gapPercent(price.price, targetValue) : null;
  const crossed = price && targetValue !== null ? isAlreadyCrossed(direction, targetValue, price.price) : false;

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (targetValue === null || crossed || !symbol) {
      return;
    }
    createAlert.mutate(
      { symbol, direction, targetPrice: targetValue },
      {
        onSuccess: () => {
          toast('success', `Alert set: ${symbol} ${direction.toLowerCase()} ${formatRupees(targetValue)}`);
          setTarget('');
          setTouched(false);
        },
        onError: (error) => toast('error', errorMessage(error)),
      },
    );
  }

  const segment = 'flex flex-1 items-center justify-center gap-1 rounded-md px-2 py-1.5 text-xs font-semibold whitespace-nowrap';

  return (
    <section aria-label="Create alert" className="rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="text-sm font-semibold">Create alert</h2>
      <form onSubmit={submit} noValidate className="mt-3 space-y-3">
        <label className="block text-xs font-medium text-slate-500">
          Stock
          <select
            value={symbol}
            onChange={(event) => onSymbolChange(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-900 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
          >
            {instruments.map((item) => (
              <option key={item.symbol} value={item.symbol}>
                {item.symbol} · {item.name}
              </option>
            ))}
          </select>
        </label>

        <div className="flex min-h-5 items-center justify-between gap-2 text-sm">
          <span className="text-slate-500">Current price</span>
          <span className="flex items-center gap-2">
            <span className="font-mono font-medium">{formatRupees(price?.price)}</span>
            {price && <SourceBadge source={price.source} />}
          </span>
        </div>

        <div role="radiogroup" aria-label="Direction" className="flex gap-1 rounded-lg bg-slate-100 p-1">
          <button
            type="button"
            role="radio"
            aria-checked={direction === 'ABOVE'}
            onClick={() => setDirection('ABOVE')}
            className={`${segment} ${direction === 'ABOVE' ? 'bg-emerald-600 text-white shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
          >
            CROSSES ABOVE <span aria-hidden>▲</span>
          </button>
          <button
            type="button"
            role="radio"
            aria-checked={direction === 'BELOW'}
            onClick={() => setDirection('BELOW')}
            className={`${segment} ${direction === 'BELOW' ? 'bg-rose-600 text-white shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
          >
            CROSSES BELOW <span aria-hidden>▼</span>
          </button>
        </div>

        <label className="block text-xs font-medium text-slate-500">
          Target price
          <span className="relative mt-1 block">
            <span aria-hidden className="absolute top-2 left-3 font-mono text-sm text-slate-500">
              ₹
            </span>
            <input
              ref={targetInput}
              type="number"
              inputMode="decimal"
              min="0.05"
              step="0.05"
              placeholder="0.00"
              value={target}
              aria-invalid={touched && Boolean(inputError)}
              onChange={(event) => setTarget(event.target.value)}
              onBlur={() => setTouched(target !== '')}
              className="w-full rounded-lg border border-slate-200 py-2 pr-3 pl-7 font-mono text-sm text-slate-900 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20 aria-invalid:border-rose-600"
            />
          </span>
        </label>

        {touched && inputError && <p className="text-xs text-rose-700">{inputError}</p>}

        {gap !== null && (
          <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <Badge tone={crossed ? 'amber' : gap >= 0 ? 'emerald' : 'rose'}>
              <span className="font-mono">{formatPercent(gap)}</span>
            </Badge>
            {crossed ? (
              <span className="text-amber-800">
                Already {direction.toLowerCase()} this target. {direction === 'ABOVE' ? 'Raise' : 'Lower'} it to set an alert.
              </span>
            ) : (
              <span>from current price</span>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={createAlert.isPending || crossed || !symbol}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {createAlert.isPending && <Loader2 aria-hidden className="size-4 animate-spin" />}
          Set alert
        </button>
      </form>
    </section>
  );
}
