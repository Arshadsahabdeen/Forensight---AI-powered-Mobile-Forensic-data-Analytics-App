import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { Spinner } from '../components/ui/Feedback';
import type { Role } from '../types/auth';

function FullPageSpinner({ label }: { label: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <Spinner label={label} />
    </div>
  );
}

export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <FullPageSpinner label="Restoring your session…" />;
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
}

export function RoleProtectedRoute({ roles }: { roles: Role[] }) {
  const { isAuthenticated, isLoading, hasRole } = useAuth();
  if (isLoading) return <FullPageSpinner label="Checking permissions…" />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return hasRole(roles) ? <Outlet /> : <Navigate to="/unauthorized" replace />;
}
