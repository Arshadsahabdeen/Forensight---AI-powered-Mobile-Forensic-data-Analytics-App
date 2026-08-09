import { ShieldAlert } from 'lucide-react';
import { Link } from 'react-router-dom';

export function UnauthorizedPage() {
  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="panel max-w-md rounded-lg p-8 text-center">
        <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-lg border border-red-400/30 bg-red-400/10">
          <ShieldAlert className="h-7 w-7 text-red-200" />
        </div>
        <p className="text-sm font-semibold uppercase text-red-200">403</p>
        <h1 className="mt-2 text-2xl font-semibold text-white">Access Denied</h1>
        <p className="mt-3 text-sm text-slate-400">You do not have permission to access this module.</p>
        <Link className="mt-6 inline-flex items-center justify-center rounded-md bg-cyanline px-4 py-2 text-sm font-semibold text-ink transition hover:bg-[#73cad9]" to="/dashboard">
          Return to dashboard
        </Link>
      </div>
    </div>
  );
}
