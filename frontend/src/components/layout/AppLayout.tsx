import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { Breadcrumbs } from './Breadcrumbs';

export function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  return (
    <div>
      <div className="print-hide"><Sidebar collapsed={collapsed} /></div>
      <div className="print-hide"><Topbar collapsed={collapsed} toggle={() => setCollapsed((value) => !value)} /></div>
      <main className={`app-main min-h-screen pt-20 transition-all ${collapsed ? 'pl-20' : 'pl-72'}`}>
        <div className="mx-auto max-w-[1600px] px-6 pb-8">
          <div className="print-hide"><Breadcrumbs /></div>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
