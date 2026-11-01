import { useAuth } from './auth/AuthContext';
import { AuthScreen } from './components/AuthScreen';
import { Dashboard } from './components/Dashboard';

export function App() {
  const { session } = useAuth();
  // Keying by user resets all page state when a different account signs in.
  return session ? <Dashboard key={session.userId} userId={session.userId} /> : <AuthScreen />;
}
