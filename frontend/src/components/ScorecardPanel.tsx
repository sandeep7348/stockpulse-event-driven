import { ClipboardList, Loader2 } from 'lucide-react';

import { errorMessage } from '../api/errors';
import { useScorecard } from '../hooks/queries';
import { formatDateTime } from '../lib/format';
import type { ScoreBand, ScoreCategory, ScoreConfidence } from '../types/api';
import { Badge, type TONES } from './Badges';
import { EmptyState } from './EmptyState';

const BANDS: Record<ScoreBand, { label: string; tone: keyof typeof TONES; meaning: string }> = {
  STRONG: { label: 'Scores well', tone: 'emerald', meaning: 'Meets most of these rules.' },
  MIXED: { label: 'Mixed', tone: 'amber', meaning: 'Meets some of these rules and misses others.' },
  WEAK: { label: 'Scores poorly', tone: 'rose', meaning: 'Misses most of these rules.' },
  INSUFFICIENT_DATA: { label: 'Not enough data', tone: 'slate', meaning: 'Too little data is available to give a score.' },
};

const CONFIDENCE: Record<ScoreConfidence, { label: string; tone: keyof typeof TONES }> = {
  HIGH: { label: 'High', tone: 'emerald' },
  MEDIUM: { label: 'Medium', tone: 'amber' },
  LOW: { label: 'Low', tone: 'rose' },
};

function ScoreBar({ score, label }: { score: number | null; label: string }) {
  return (
    <div
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={score ?? undefined}
      aria-valuetext={score === null ? 'No data' : `${score} out of 100`}
      className="h-1.5 w-full overflow-hidden rounded-full bg-blue-100"
    >
      {score !== null && <div className="h-full rounded-full bg-blue-600" style={{ width: `${score}%` }} />}
    </div>
  );
}

function CategoryRow({ category }: { category: ScoreCategory }) {
  return (
    <details className="group border-t border-slate-100 py-2.5 first:border-t-0">
      <summary className="flex cursor-pointer list-none items-center gap-3 [&::-webkit-details-marker]:hidden">
        <span className="w-32 shrink-0 text-sm text-slate-900 sm:w-36">{category.name}</span>
        <span className="min-w-0 flex-1">
          <ScoreBar score={category.score} label={`${category.name} score`} />
        </span>
        <span className="w-8 shrink-0 text-right font-mono text-sm font-medium">{category.score ?? '—'}</span>
        <span aria-hidden className="w-3 shrink-0 text-xs text-slate-400 group-open:rotate-90">
          ▸
        </span>
      </summary>
      <dl className="mt-2 space-y-2.5 rounded-lg bg-slate-50 p-3">
        {category.parameters.map((parameter) => (
          <div key={parameter.name}>
            <div className="flex items-baseline justify-between gap-3">
              <dt className="text-sm font-medium text-slate-900">{parameter.name}</dt>
              <dd className="text-right text-sm whitespace-nowrap">
                <span className="font-mono font-medium">{parameter.value ?? 'No data'}</span>
                {parameter.score !== null && (
                  <span className="ml-2 font-mono text-xs text-slate-500">{parameter.score}/100</span>
                )}
              </dd>
            </div>
            <p className="mt-0.5 text-xs text-slate-500">{parameter.rule}</p>
          </div>
        ))}
      </dl>
    </details>
  );
}

export function ScorecardPanel({ symbol }: { symbol: string }) {
  const scorecard = useScorecard(symbol);
  const data = scorecard.data;

  return (
    <section aria-label="Stock score" className="min-w-0 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center gap-2">
        <h2 className="text-sm font-semibold">{symbol ? `${symbol} score` : 'Stock score'}</h2>
        {scorecard.isFetching && <Loader2 aria-label="Updating score" className="size-3.5 animate-spin text-slate-400" />}
        {data?.stale && (
          <Badge tone="amber" title="The latest update failed, so this is a saved copy">
            Saved {formatDateTime(data.fetchedAt)}
          </Badge>
        )}
      </div>

      {scorecard.isPending && symbol && (
        <div className="flex h-40 items-center justify-center">
          <Loader2 aria-label="Loading score" className="size-5 animate-spin text-slate-400" />
        </div>
      )}
      {scorecard.isError && !data && (
        <EmptyState icon={ClipboardList} title="Score isn't available" hint={errorMessage(scorecard.error)} />
      )}

      {data && (
        <>
          <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-3">
            <div className="flex items-baseline gap-1">
              <span className="text-4xl font-semibold tracking-tight">{data.score ?? '—'}</span>
              <span className="text-sm text-slate-500">/ 100</span>
            </div>
            <div>
              <Badge tone={BANDS[data.band].tone}>{BANDS[data.band].label}</Badge>
              <p className="mt-1 text-xs text-slate-500">{BANDS[data.band].meaning}</p>
            </div>
            <div>
              <div className="flex items-center gap-1.5 text-xs text-slate-500">
                Data confidence <Badge tone={CONFIDENCE[data.confidence].tone}>{CONFIDENCE[data.confidence].label}</Badge>
              </div>
              <p className="mt-1 text-xs text-slate-500">
                {data.parametersAvailable} of {data.parametersTotal} parameters available
                {data.financialsAsOf ? `, results to ${data.financialsAsOf}` : ', no company results found'}
              </p>
            </div>
          </div>

          <div className="mt-3 border-t border-slate-200 pt-1">
            {data.categories.map((category) => (
              <CategoryRow key={category.name} category={category} />
            ))}
          </div>

          {data.financialCompany && (
            <p className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
              Treated as a lender or insurer from its name, so debt and cash flow aren't scored: a bank's deposits count as
              debt, which would be misleading.
            </p>
          )}

          <p className="mt-3 text-xs text-slate-500">
            <strong className="font-medium text-slate-600">This isn't a prediction or a recommendation.</strong> The score
            compares reported results and past prices with fixed rules of thumb that are the same for every sector, so
            normal differences between industries affect it. It hasn't been tested as a way of forecasting returns. Data
            confidence means how complete and recent the data is, not how likely the price is to rise. Data is from Yahoo
            Finance's unofficial source and may contain errors.
          </p>
        </>
      )}
    </section>
  );
}
