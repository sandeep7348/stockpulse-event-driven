import { isMarketOpen } from '../lib/format';
import type {
  Alert,
  AlertStatus,
  ChartRange,
  CreateAlertRequest,
  Instrument,
  Notification,
  Page,
  PageQuery,
  PriceHistory,
  ScoreCategory,
  ScoreParameter,
  Scorecard,
  StockAlertApi,
} from '../types/api';

// Sample data for previewing the UI without a backend. Nothing here is market data, and
// the app shows a "Demo data" banner whenever this module is in use.

const INSTRUMENTS: (Instrument & { base: number })[] = [
  { symbol: 'RELIANCE', name: 'Reliance Industries', isin: 'INE002A01018', exchange: 'NSE', base: 1400 },
  { symbol: 'TCS', name: 'Tata Consultancy Services', isin: 'INE467B01029', exchange: 'NSE', base: 3500 },
  { symbol: 'HDFCBANK', name: 'HDFC Bank', isin: 'INE040A01034', exchange: 'NSE', base: 1700 },
  { symbol: 'INFY', name: 'Infosys', isin: 'INE009A01021', exchange: 'NSE', base: 1500 },
  { symbol: 'ICICIBANK', name: 'ICICI Bank', isin: 'INE090A01021', exchange: 'NSE', base: 1300 },
  { symbol: 'SBIN', name: 'State Bank of India', isin: 'INE062A01020', exchange: 'NSE', base: 800 },
  { symbol: 'BHARTIARTL', name: 'Bharti Airtel', isin: 'INE397D01024', exchange: 'NSE', base: 1800 },
  { symbol: 'ITC', name: 'ITC', isin: 'INE154A01025', exchange: 'NSE', base: 420 },
  { symbol: 'LT', name: 'Larsen & Toubro', isin: 'INE018A01030', exchange: 'NSE', base: 3500 },
  { symbol: 'HINDUNILVR', name: 'Hindustan Unilever', isin: 'INE030A01027', exchange: 'NSE', base: 2400 },
];

// Found only through search, like the rest of NSE in the real app.
const SEARCH_ONLY: (Instrument & { base: number })[] = [
  { symbol: 'WIPRO', name: 'Wipro', isin: 'INE075A01022', exchange: 'NSE', base: 200 },
  { symbol: 'AXISBANK', name: 'Axis Bank', isin: 'INE238A01034', exchange: 'NSE', base: 1100 },
  { symbol: 'MARUTI', name: 'Maruti Suzuki India', isin: 'INE585B01010', exchange: 'NSE', base: 12000 },
  { symbol: 'M&M', name: 'Mahindra & Mahindra', isin: 'INE101A01026', exchange: 'NSE', base: 2800 },
  { symbol: 'KOTAKBANK', name: 'Kotak Mahindra Bank', isin: 'INE237A01036', exchange: 'NSE', base: 1800 },
];

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

const prices = new Map([...INSTRUMENTS, ...SEARCH_ONLY].map((instrument) => [instrument.symbol, instrument.base]));
let nextId = 100;

const alerts: Alert[] = [
  alert('a1', 'RELIANCE', 'ABOVE', 1450, 'ACTIVE', 95),
  alert('a2', 'SBIN', 'BELOW', 760, 'ACTIVE', 240),
  alert('a3', 'TCS', 'ABOVE', 3620.5, 'ACTIVE', 1440),
  alert('a4', 'INFY', 'BELOW', 1480, 'TRIGGERED', 2880, 1479.9, 300),
  alert('a5', 'ITC', 'ABOVE', 415, 'TRIGGERED', 4320, 415.1, 1500),
  alert('a6', 'LT', 'ABOVE', 3900, 'CANCELLED', 5760),
];

const notifications: Notification[] = [
  notification('a4', 'INFY', 'BELOW', 1480, 1479.9, 300),
  notification('a5', 'ITC', 'ABOVE', 415, 415.1, 1500),
];

function alert(
  id: string,
  symbol: string,
  direction: Alert['direction'],
  targetPrice: number,
  status: AlertStatus,
  createdMinutesAgo: number,
  triggeredPrice: number | null = null,
  triggeredMinutesAgo = 0,
): Alert {
  return {
    id,
    symbol,
    direction,
    targetPrice,
    status,
    createdAt: minutesAgo(createdMinutesAgo),
    triggeredAt: triggeredPrice === null ? null : minutesAgo(triggeredMinutesAgo),
    triggeredPrice,
  };
}

function notification(
  alertId: string,
  symbol: string,
  direction: Alert['direction'],
  target: number,
  hit: number,
  createdMinutesAgo: number,
): Notification {
  return {
    id: `n-${alertId}`,
    alertId,
    symbol,
    triggeredPrice: hit,
    message: `${symbol} crossed ${direction} Rs ${target.toFixed(2)}: now Rs ${hit.toFixed(2)}`,
    createdAt: minutesAgo(createdMinutesAgo),
  };
}

function toPage<T>(items: T[], { page, size }: PageQuery): Page<T> {
  return {
    content: items.slice(page * size, (page + 1) * size),
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
    number: page,
    size,
  };
}

function isCrossed(direction: Alert['direction'], target: number, price: number) {
  return direction === 'ABOVE' ? price >= target : price <= target;
}

function movePrice(symbol: string) {
  const current = prices.get(symbol)!;
  const next = Math.round(current * (1 + (Math.random() - 0.5) * 0.002) * 20) / 20;
  prices.set(symbol, next);
  for (const active of alerts) {
    if (active.symbol === symbol && active.status === 'ACTIVE' && isCrossed(active.direction, active.targetPrice, next)) {
      active.status = 'TRIGGERED';
      active.triggeredAt = new Date().toISOString();
      active.triggeredPrice = next;
      notifications.unshift(notification(active.id, symbol, active.direction, active.targetPrice, next, 0));
    }
  }
  return next;
}

const HISTORY_SHAPE: Record<ChartRange, { points: number; stepMinutes: number }> = {
  '1D': { points: 75, stepMinutes: 5 },
  '1W': { points: 65, stepMinutes: 30 },
  '1M': { points: 22, stepMinutes: 1440 },
  '6M': { points: 126, stepMinutes: 1440 },
  '1Y': { points: 248, stepMinutes: 1440 },
  '5Y': { points: 260, stepMinutes: 10080 },
};

function mockHistory(symbol: string, range: ChartRange): PriceHistory {
  const { points, stepMinutes } = HISTORY_SHAPE[range];
  const warmup = 50;
  const closes: number[] = [prices.get(symbol)!];
  for (let i = 1; i < points + warmup; i++) {
    closes.unshift(Math.round(closes[0] * (1 + (Math.random() - 0.5) * 0.03) * 20) / 20);
  }
  const average = (end: number, period: number) => {
    const slice = closes.slice(end - period + 1, end + 1);
    return Math.round((slice.reduce((sum, value) => sum + value, 0) / period) * 100) / 100;
  };
  const rsi = (end: number) => {
    let gain = 0;
    let loss = 0;
    for (let i = end - 13; i <= end; i++) {
      const change = closes[i] - closes[i - 1];
      gain += Math.max(change, 0);
      loss += Math.max(-change, 0);
    }
    return loss === 0 ? 100 : Math.round((100 - 100 / (1 + gain / loss)) * 100) / 100;
  };
  const result = closes.slice(warmup).map((close, index) => {
    const at = index + warmup;
    const open = closes[at - 1];
    return {
      time: new Date(Date.now() - (points - 1 - index) * stepMinutes * 60_000).toISOString(),
      open,
      high: Math.round(Math.max(open, close) * 1.005 * 100) / 100,
      low: Math.round(Math.min(open, close) * 0.995 * 100) / 100,
      close,
      volume: Math.round(500_000 + Math.random() * 1_500_000),
      ma20: average(at, 20),
      ma50: average(at, 50),
      rsi14: rsi(at),
    };
  });
  return {
    symbol,
    range,
    interval: 'sample',
    currency: 'INR',
    source: 'simulated',
    fiftyTwoWeekHigh: Math.max(...closes),
    fiftyTwoWeekLow: Math.min(...closes),
    fetchedAt: new Date().toISOString(),
    stale: false,
    points: result,
  };
}

function mockScorecard(symbol: string): Scorecard {
  const parameter = (name: string, value: string, score: number): ScoreParameter => ({
    name,
    value,
    score,
    rule: 'Sample rule for the demo.',
  });
  const category = (name: string, parameters: ScoreParameter[]): ScoreCategory => ({
    name,
    score: Math.round(parameters.reduce((sum, item) => sum + (item.score ?? 0), 0) / parameters.length),
    parameters,
  });
  const categories = [
    category('Profitability', [parameter('Return on equity', '14.0%', 93), parameter('Net profit margin', '9.0%', 60)]),
    category('Growth', [parameter('Revenue growth', '8.0%', 53), parameter('Profit growth', '6.0%', 40)]),
    category('Financial health', [parameter('Debt to equity', '0.60', 93), parameter('Cash conversion', '1.10', 100)]),
    category('Valuation', [parameter('P/E ratio', '24.00', 64), parameter('P/B ratio', '3.00', 67)]),
    category('Price trend', [parameter('Price vs 200-day average', '+2.0%', 60), parameter('1-year return', '+5.0%', 50)]),
    category('Risk', [parameter('Volatility', '28.0%', 80), parameter('Largest fall', '22.0%', 84)]),
  ];
  return {
    symbol,
    score: Math.round(categories.reduce((sum, item) => sum + (item.score ?? 0), 0) / categories.length),
    band: 'MIXED',
    confidence: 'HIGH',
    parametersAvailable: 12,
    parametersTotal: 12,
    financialsAsOf: '2026-03-31',
    financialCompany: false,
    categories,
    fetchedAt: new Date().toISOString(),
    stale: false,
  };
}

function fakeToken(email: string) {
  const encode = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '');
  const exp = Math.floor(Date.now() / 1000) + 3600;
  return `${encode({ alg: 'none' })}.${encode({ sub: email, exp })}.demo`;
}

const delay = <T>(value: T) => new Promise<T>((resolve) => setTimeout(() => resolve(value), 150));

export const mockApi: StockAlertApi = {
  login: ({ email }) => delay({ token: fakeToken(email) }),
  register: ({ email }) => delay({ token: fakeToken(email) }),
  marketStatus: () =>
    delay({ exchange: 'NSE', open: isMarketOpen(new Date()), timeZone: 'Asia/Kolkata', priceSource: 'simulated' }),
  instruments: () => delay(INSTRUMENTS.map(({ base: _base, ...instrument }) => instrument)),
  searchInstruments: (term) => {
    const lower = term.trim().toLowerCase();
    return delay(
      [...INSTRUMENTS, ...SEARCH_ONLY]
        .filter((item) => [item.symbol, item.name, item.isin].some((value) => value.toLowerCase().includes(lower)))
        .map(({ base: _base, ...instrument }) => instrument),
    );
  },
  price: (symbol) =>
    delay({
      symbol,
      currency: 'INR',
      price: movePrice(symbol),
      timestamp: new Date().toISOString(),
      source: 'simulated',
    }),
  history: (symbol, range) => delay(mockHistory(symbol, range)),
  scorecard: (symbol) => delay(mockScorecard(symbol)),
  alerts: (statuses, query) =>
    delay(
      toPage(
        alerts
          .filter((item) => statuses.includes(item.status))
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
        query,
      ),
    ),
  createAlert: ({ symbol, direction, targetPrice }: CreateAlertRequest) => {
    const price = prices.get(symbol)!;
    if (isCrossed(direction, targetPrice, price)) {
      return Promise.reject(new Error(`${symbol} is already ${direction} this target at Rs ${price.toFixed(2)}`));
    }
    const created = alert(`m${nextId++}`, symbol, direction, targetPrice, 'ACTIVE', 0);
    alerts.unshift(created);
    return delay(created);
  },
  cancelAlert: (id) => {
    const found = alerts.find((item) => item.id === id);
    if (found) {
      found.status = 'CANCELLED';
    }
    return delay(undefined);
  },
  notifications: (query) => delay(toPage(notifications, query)),
};
