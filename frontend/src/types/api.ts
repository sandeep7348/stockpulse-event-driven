// Mirrors the Spring Boot response records. All prices are rupees, already converted from
// paise by the backend, so they must not be divided by 100 again.

export type Direction = 'ABOVE' | 'BELOW';
export type AlertStatus = 'ACTIVE' | 'TRIGGERED' | 'CANCELLED';
export type PriceSource = 'simulated' | 'upstox' | (string & {});

export interface TokenResponse {
  token: string;
}

export interface Credentials {
  email: string;
  password: string;
}

export interface MarketStatus {
  exchange: string;
  open: boolean;
  timeZone: string;
  priceSource: PriceSource;
}

export interface Instrument {
  symbol: string;
  name: string;
  isin: string;
  exchange: string;
}

export interface Price {
  symbol: string;
  currency: string;
  price: number;
  timestamp: string;
  source: PriceSource;
}

export type ChartRange = '1D' | '1W' | '1M' | '6M' | '1Y' | '5Y';

export interface HistoryPoint {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  ma20: number | null;
  ma50: number | null;
  rsi14: number | null;
}

export interface PriceHistory {
  symbol: string;
  range: ChartRange;
  interval: string;
  currency: string;
  source: PriceSource;
  fiftyTwoWeekHigh: number | null;
  fiftyTwoWeekLow: number | null;
  fetchedAt: string;
  stale: boolean;
  points: HistoryPoint[];
}

export interface ScoreParameter {
  name: string;
  value: string | null;
  score: number | null;
  rule: string;
}

export interface ScoreCategory {
  name: string;
  score: number | null;
  parameters: ScoreParameter[];
}

export type ScoreBand = 'STRONG' | 'MIXED' | 'WEAK' | 'INSUFFICIENT_DATA';
export type ScoreConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export interface Scorecard {
  symbol: string;
  score: number | null;
  band: ScoreBand;
  /** How complete and recent the data is, not how likely the price is to rise. */
  confidence: ScoreConfidence;
  parametersAvailable: number;
  parametersTotal: number;
  financialsAsOf: string | null;
  financialCompany: boolean;
  categories: ScoreCategory[];
  fetchedAt: string;
  stale: boolean;
}

export interface Alert {
  id: string;
  symbol: string;
  direction: Direction;
  targetPrice: number;
  status: AlertStatus;
  createdAt: string;
  triggeredAt: string | null;
  triggeredPrice: number | null;
}

export interface CreateAlertRequest {
  symbol: string;
  direction: Direction;
  targetPrice: number;
}

export interface Notification {
  id: string;
  alertId: string;
  symbol: string | null;
  triggeredPrice: number | null;
  message: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** The backend's wire format for lists (Spring Data's PagedModel). */
export interface PagedModel<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface PageQuery {
  page: number;
  size: number;
}

/** Returns null when the backend has not received a price for the symbol yet. */
export interface StockAlertApi {
  login(credentials: Credentials): Promise<TokenResponse>;
  register(credentials: Credentials): Promise<TokenResponse>;
  marketStatus(): Promise<MarketStatus>;
  instruments(): Promise<Instrument[]>;
  searchInstruments(term: string): Promise<Instrument[]>;
  price(symbol: string): Promise<Price | null>;
  history(symbol: string, range: ChartRange): Promise<PriceHistory>;
  scorecard(symbol: string): Promise<Scorecard>;
  alerts(statuses: AlertStatus[], query: PageQuery): Promise<Page<Alert>>;
  createAlert(request: CreateAlertRequest): Promise<Alert>;
  cancelAlert(id: string): Promise<void>;
  notifications(query: PageQuery): Promise<Page<Notification>>;
}
