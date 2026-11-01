import { AlertTriangle } from 'lucide-react';
import { Component, type ErrorInfo, type ReactNode } from 'react';

/** Without this, any error while rendering unmounts the whole app and leaves a blank page. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('The page crashed while rendering', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <main className="grid min-h-dvh place-items-center bg-slate-50 p-4">
        <div role="alert" className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-6 text-center shadow-sm">
          <span className="mx-auto mb-3 grid size-11 place-items-center rounded-full bg-rose-50">
            <AlertTriangle aria-hidden className="size-5 text-rose-600" />
          </span>
          <h1 className="text-base font-semibold">Something went wrong on this page</h1>
          <p className="mt-1 text-sm text-slate-500">Your alerts are safe. They're stored on the server and keep running.</p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-5 w-full rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            Reload the page
          </button>
        </div>
      </main>
    );
  }
}
