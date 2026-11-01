import axios, { isAxiosError } from 'axios';

import type { Page, PagedModel, StockAlertApi } from '../types/api';

export function toPage<T>({ content, page }: PagedModel<T>): Page<T> {
  return { content, ...page };
}

let authToken: string | null = null;
let onUnauthorized: () => void = () => {};

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '', timeout: 15000 });

client.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
  }
  return config;
});

client.interceptors.response.use(undefined, (error: unknown) => {
  // A 401 from the login form means wrong credentials, not an expired session.
  if (isAxiosError(error) && error.response?.status === 401 && !error.config?.url?.startsWith('/api/auth')) {
    onUnauthorized();
  }
  return Promise.reject(error);
});

export const httpApi: StockAlertApi = {
  login: async (credentials) => (await client.post('/api/auth/login', credentials)).data,
  register: async (credentials) => (await client.post('/api/auth/register', credentials)).data,
  marketStatus: async () => (await client.get('/api/market/status')).data,
  instruments: async () => (await client.get('/api/market/instruments')).data,
  searchInstruments: async (term) => (await client.get('/api/market/instruments/search', { params: { q: term } })).data,
  price: async (symbol) => {
    try {
      return (await client.get(`/api/market/prices/${encodeURIComponent(symbol)}`)).data;
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },
  history: async (symbol, range) =>
    (await client.get(`/api/market/history/${encodeURIComponent(symbol)}`, { params: { range } })).data,
  scorecard: async (symbol) => (await client.get(`/api/market/scorecard/${encodeURIComponent(symbol)}`)).data,
  alerts: async (statuses, { page, size }) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    statuses.forEach((status) => params.append('status', status));
    return toPage((await client.get('/api/alerts', { params })).data);
  },
  createAlert: async (request) => (await client.post('/api/alerts', request)).data,
  cancelAlert: async (id) => {
    await client.delete(`/api/alerts/${encodeURIComponent(id)}`);
  },
  notifications: async ({ page, size }) =>
    toPage((await client.get('/api/notifications', { params: { page, size } })).data),
};
