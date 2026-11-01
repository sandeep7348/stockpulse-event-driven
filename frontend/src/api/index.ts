import { httpApi } from './http';
import { mockApi } from './mock';

/** Mock data only runs when explicitly requested, so production builds always use the real API. */
export const isDemoMode = import.meta.env.VITE_API_MODE === 'mock';

export const api = isDemoMode ? mockApi : httpApi;
