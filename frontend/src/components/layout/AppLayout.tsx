import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { getSessionPolicy } from '../../services/forensics';
import { useIdleLogout } from '../../hooks/useIdleLogout';
import { useAuth } from '../../contexts/AuthContext';

export function AppLayout() {
  const [navOpen, setNavOpen] = useState(false);
  const { hasRole } = useAuth();
  const policy = useQuery({ queryKey: ['session-policy'], queryFn: getSessionPolicy, staleTime: 5 * 60_000 });
  useIdleLogout(policy.data?.sessionTimeoutMinutes);

  return (
    <div className="min-h-screen">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
      <div className="lg:pl-60">
        <Topbar onMenu={() => setNavOpen(true)} />
        {policy.data?.maintenanceMode && (
          <div role="status" className="border-b border-amber-200 bg-amber-50 px-6 py-2 text-sm text-amber-900">
            Maintenance mode is on. {hasRole(['ADMIN']) ? 'Only administrators can make changes.' : 'Changes are temporarily disabled.'}
          </div>
        )}
        <main id="main" className="mx-auto max-w-[1400px] px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
