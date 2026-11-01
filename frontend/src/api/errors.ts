import { isAxiosError } from 'axios';

/** Shows the backend's ProblemDetail message when it sends one. */
export function errorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: unknown } | undefined)?.detail;
    if (typeof detail === 'string' && detail) {
      return detail;
    }
    if (!error.response) {
      return 'Cannot reach the server. Check that the backend is running.';
    }
    return `The server returned an error (${error.response.status}).`;
  }
  return error instanceof Error ? error.message : 'Something went wrong.';
}
