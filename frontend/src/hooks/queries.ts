import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';

import { api } from '../api';
import type { AlertStatus, ChartRange, CreateAlertRequest, Price } from '../types/api';

const PRICE_POLL_MS = 5_000;
const STATUS_POLL_MS = 30_000;
const LIST_POLL_MS = 10_000;
const HISTORY_POLL_MS = 60_000;

/** The API's largest page; the unread count looks at this many recent notifications. */
export const NOTIFICATION_PAGE_SIZE = 100;

export function useMarketStatus() {
  return useQuery({ queryKey: ['market-status'], queryFn: api.marketStatus, refetchInterval: STATUS_POLL_MS });
}

export function useInstruments() {
  return useQuery({ queryKey: ['instruments'], queryFn: api.instruments, staleTime: Infinity });
}

export function useInstrumentSearch(term: string) {
  const trimmed = term.trim();
  return useQuery({
    queryKey: ['instrument-search', trimmed.toLowerCase()],
    queryFn: () => api.searchInstruments(trimmed),
    enabled: trimmed.length > 0,
    staleTime: 5 * 60_000,
    placeholderData: (previous) => previous,
  });
}

export function usePrices(symbols: string[]) {
  const results = useQueries({
    queries: symbols.map((symbol) => ({
      queryKey: ['price', symbol],
      queryFn: () => api.price(symbol),
      refetchInterval: PRICE_POLL_MS,
    })),
  });
  const signature = results.map((result) => result.dataUpdatedAt).join();
  return useMemo(() => {
    const prices = new Map<string, Price>();
    results.forEach((result) => {
      if (result.data) {
        prices.set(result.data.symbol, result.data);
      }
    });
    return prices;
    // useQueries returns a new array each render; the timestamps show when data really changed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);
}

export function useHistory(symbol: string, range: ChartRange) {
  return useQuery({
    queryKey: ['history', symbol, range],
    queryFn: () => api.history(symbol, range),
    enabled: symbol.length > 0,
    refetchInterval: HISTORY_POLL_MS,
    // Switching range keeps the current chart on screen, but another stock's chart must never show.
    placeholderData: (previous) => (previous?.symbol === symbol ? previous : undefined),
  });
}

export function useScorecard(symbol: string) {
  return useQuery({
    queryKey: ['scorecard', symbol],
    queryFn: () => api.scorecard(symbol),
    enabled: symbol.length > 0,
    staleTime: 30 * 60_000,
  });
}

export function useAlerts(statuses: AlertStatus[], page: number, size: number) {
  return useQuery({
    queryKey: ['alerts', statuses, page, size],
    queryFn: () => api.alerts(statuses, { page, size }),
    refetchInterval: LIST_POLL_MS,
    placeholderData: (previous) => previous,
  });
}

export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.notifications({ page: 0, size: NOTIFICATION_PAGE_SIZE }),
    refetchInterval: LIST_POLL_MS,
  });
}

export function useCreateAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateAlertRequest) => api.createAlert(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  });
}

export function useCancelAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.cancelAlert(id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  });
}
