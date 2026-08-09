import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { Breadcrumbs } from './Breadcrumbs';

export function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  return (
    <div>
      <Sidebar collapsed={collapsed} />
      <Topbar collapsed={collapsed} toggle={() => setCollapsed((value) => !value)} />
      <main className={`min-h-screen pt-20 transition-all ${collapsed ? 'pl-20' : 'pl-72'}`}>
        <div className="mx-auto max-w-[1600px] px-6 pb-8">
          <Breadcrumbs />
          <Outlet />
        </div>
      </main>
    </div>
  );
}
