import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Loader2, Lock, ShieldCheck, User } from 'lucide-react';
import { motion } from 'framer-motion';
import { Logo } from '../../components/common/Logo';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { useAuth } from '../../contexts/AuthContext';

interface FormValues {
  username: string;
  password: string;
}

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const { register, handleSubmit, formState } = useForm<FormValues>({
    defaultValues: { username: 'admin', password: 'Admin@123' }
  });

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  async function onSubmit(values: FormValues) {
    setError('');
    try {
      await login(values.username, values.password);
      navigate('/dashboard', { replace: true });
    } catch {
      setError('Invalid credentials or authentication service unavailable.');
    }
  }

  return (
    <main className="grid min-h-screen lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden border-r border-slate-800 bg-[#0b1117] p-12 lg:block">
        <div className="absolute inset-0 bg-[linear-gradient(rgba(70,179,199,0.05)_1px,transparent_1px),linear-gradient(90deg,rgba(70,179,199,0.05)_1px,transparent_1px)] bg-[size:38px_38px]" />
        <div className="relative z-10 flex h-full flex-col justify-between">
          <Logo />
          <div className="max-w-2xl">
            <div className="mb-5 inline-flex items-center gap-2 rounded-md border border-cyanline/30 bg-cyanline/10 px-3 py-2 text-sm text-cyanline">
              <ShieldCheck className="h-4 w-4" />
              Secure evidence intelligence workspace
            </div>
            <h1 className="text-5xl font-semibold leading-tight text-white">AI-Powered Mobile Forensic Evidence Intelligence</h1>
            <p className="mt-5 max-w-xl text-base leading-7 text-slate-400">
              Enterprise investigation console for case triage, artifact review, audit readiness, and upcoming AI-assisted evidence analysis.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-3 text-sm text-slate-400">
            {['JWT secured', 'Audit tracked', 'Role based'].map((item) => (
              <div key={item} className="rounded-lg border border-slate-800 bg-white/[0.03] p-4">{item}</div>
            ))}
          </div>
        </div>
      </section>
      <section className="flex items-center justify-center px-6 py-10">
        <motion.form
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          onSubmit={handleSubmit(onSubmit)}
          className="panel w-full max-w-md rounded-lg p-8"
        >
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h2 className="text-2xl font-semibold text-white">Investigation Login</h2>
          <p className="mt-2 text-sm text-slate-400">Access the protected forensic intelligence platform.</p>
          <div className="mt-8 space-y-5">
            <label className="block">
              <span className="mb-2 flex items-center gap-2 text-sm text-slate-300"><User className="h-4 w-4" /> Username or email</span>
              <Input {...register('username', { required: 'Username is required' })} autoComplete="username" />
              {formState.errors.username && <span className="mt-1 block text-xs text-red-300">{formState.errors.username.message}</span>}
            </label>
            <label className="block">
              <span className="mb-2 flex items-center gap-2 text-sm text-slate-300"><Lock className="h-4 w-4" /> Password</span>
              <div className="relative">
                <Input type={showPassword ? 'text' : 'password'} {...register('password', { required: 'Password is required' })} autoComplete="current-password" />
                <button type="button" onClick={() => setShowPassword((value) => !value)} className="absolute right-2 top-2 rounded p-1 text-slate-400 hover:text-slate-100">
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {formState.errors.password && <span className="mt-1 block text-xs text-red-300">{formState.errors.password.message}</span>}
            </label>
          </div>
          {error && <div className="mt-5 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-200">{error}</div>}
          <Button className="mt-6 w-full" disabled={formState.isSubmitting}>
            {formState.isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
            Sign in securely
          </Button>
        </motion.form>
      </section>
    </main>
  );
}
