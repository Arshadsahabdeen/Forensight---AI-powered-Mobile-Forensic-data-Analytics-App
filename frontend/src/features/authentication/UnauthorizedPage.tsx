import { ShieldAlert, SearchX } from 'lucide-react';
import { LinkButton } from '../../components/ui/Button';

export function UnauthorizedPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="max-w-md text-center">
        <ShieldAlert className="mx-auto h-8 w-8 text-amber-600" aria-hidden />
        <h1 className="mt-3 text-lg font-semibold text-slate-900">You do not have access to this page</h1>
        <p className="mt-1 text-sm text-slate-500">Your role does not include this function. Ask an administrator if you need access.</p>
        <LinkButton to="/dashboard" className="mt-6">Back to dashboard</LinkButton>
      </div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <SearchX className="mx-auto h-8 w-8 text-slate-400" aria-hidden />
      <h1 className="mt-3 text-lg font-semibold text-slate-900">Page not found</h1>
      <p className="mt-1 text-sm text-slate-500">The address may be mistyped, or the page has moved.</p>
      <LinkButton to="/dashboard" variant="secondary" className="mt-6">Back to dashboard</LinkButton>
    </div>
  );
}
