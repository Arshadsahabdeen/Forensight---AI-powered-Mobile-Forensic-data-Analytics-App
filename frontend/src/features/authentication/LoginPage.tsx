import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Lock } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { friendlyError } from '../../services/errors';
import { SESSION_NOTICE_KEY } from '../../services/api';
import { Logo } from '../../components/common/Logo';
import { Button } from '../../components/ui/Button';
import { Field, Input } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Feedback';

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    try {
      const stored = window.sessionStorage.getItem(SESSION_NOTICE_KEY);
      if (stored) {
        setNotice(stored);
        window.sessionStorage.removeItem(SESSION_NOTICE_KEY);
      }
    } catch {
      // ignore
    }
  }, []);

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/dashboard';
  if (isAuthenticated) return <Navigate to={redirectTo} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    const nextErrors: typeof errors = {};
    if (!username.trim()) nextErrors.username = 'Enter your username or email.';
    if (!password) nextErrors.password = 'Enter your password.';
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    setSubmitting(true);
    setError('');
    try {
      await login(username.trim(), password);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(friendlyError(err, 'Sign-in failed. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-card">
          <h1 className="text-lg font-semibold text-slate-900">Sign in</h1>
          <p className="mt-1 text-sm text-slate-500">Access is restricted to authorized investigation staff. Activity is audited.</p>

          <form onSubmit={submit} noValidate className="mt-6 space-y-4">
            {notice && <Alert tone="info">{notice}</Alert>}
            {error && <Alert tone="danger">{error}</Alert>}
            <Field label="Username or email" error={errors.username}>
              {(props) => (
                <Input {...props} autoComplete="username" autoFocus value={username} onChange={(event) => setUsername(event.target.value)} />
              )}
            </Field>
            <Field label="Password" error={errors.password}>
              {(props) => (
                <div className="relative">
                  <Input {...props} type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password}
                    onChange={(event) => setPassword(event.target.value)} className="pr-10" />
                  <button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-700">
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              )}
            </Field>
            <Button type="submit" className="w-full" loading={submitting} icon={<Lock className="h-4 w-4" />}>
              Sign in
            </Button>
          </form>
        </div>
        {import.meta.env.DEV && (
          <p className="mt-4 text-center text-xs text-slate-500">
            Development accounts: admin / Admin@123 · investigator / Investigator@123 · reviewer / Reviewer@123
          </p>
        )}
      </div>
    </div>
  );
}
