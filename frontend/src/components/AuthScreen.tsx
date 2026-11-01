import { Activity, Loader2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';

import { isDemoMode } from '../api';
import { errorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';

type Mode = 'login' | 'register';

export function AuthScreen() {
  const { login, register, signedOutReason } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await (mode === 'login' ? login : register)({ email, password });
    } catch (caught) {
      setError(errorMessage(caught));
      setSubmitting(false);
    }
  }

  const isLogin = mode === 'login';
  const inputClass =
    'mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20';

  return (
    <main className="grid min-h-dvh place-items-center bg-slate-50 p-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2">
          <span className="grid size-9 place-items-center rounded-lg bg-blue-600">
            <Activity aria-hidden className="size-5 text-white" />
          </span>
          <span className="text-lg font-semibold tracking-tight">NSE AlertPulse</span>
        </div>
        <form onSubmit={submit} className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h1 className="text-base font-semibold">{isLogin ? 'Sign in' : 'Create your account'}</h1>
          <p className="mt-1 text-sm text-slate-500">Get notified when NSE stocks reach your target price.</p>
          {signedOutReason && !error && (
            <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">{signedOutReason}</p>
          )}
          {isDemoMode && (
            <p className="mt-4 rounded-lg bg-blue-50 px-3 py-2 text-sm text-blue-700">
              Demo mode: enter any email and password. Nothing is sent to a server.
            </p>
          )}
          {error && (
            <p role="alert" className="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {error}
            </p>
          )}
          <label className="mt-4 block text-sm font-medium">
            Email
            <input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className={inputClass}
            />
          </label>
          <label className="mt-3 block text-sm font-medium">
            Password
            <input
              type="password"
              required
              minLength={isLogin ? undefined : 8}
              maxLength={isLogin ? undefined : 72}
              autoComplete={isLogin ? 'current-password' : 'new-password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={inputClass}
            />
            {!isLogin && <span className="mt-1 block text-xs font-normal text-slate-500">At least 8 characters.</span>}
          </label>
          <button
            type="submit"
            disabled={submitting}
            className="mt-5 flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
          >
            {submitting && <Loader2 aria-hidden className="size-4 animate-spin" />}
            {isLogin ? 'Sign in' : 'Create account'}
          </button>
          <p className="mt-4 text-center text-sm text-slate-500">
            {isLogin ? 'New here?' : 'Already have an account?'}{' '}
            <button
              type="button"
              onClick={() => {
                setMode(isLogin ? 'register' : 'login');
                setError(null);
              }}
              className="font-medium text-blue-600 hover:underline"
            >
              {isLogin ? 'Create an account' : 'Sign in'}
            </button>
          </p>
        </form>
      </div>
    </main>
  );
}
