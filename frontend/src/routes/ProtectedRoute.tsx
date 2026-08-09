import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import type { Role } from '../types/auth';

export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return <div className="min-h-screen bg-[#0b1117] p-6 text-slate-300">Restoring secure session...</div>;
  }
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}

export function RoleProtectedRoute({ roles }: { roles: Role[] }) {
  const { isAuthenticated, isLoading, hasRole } = useAuth();
  if (isLoading) {
    return <div className="min-h-screen bg-[#0b1117] p-6 text-slate-300">Checking permissions...</div>;
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return hasRole(roles) ? <Outlet /> : <Navigate to="/unauthorized" replace />;
}
