import {
  Activity,
  Bot,
  ClipboardList,
  Database,
  FileText,
  GitBranch,
  HardDrive,
  History,
  LayoutDashboard,
  Settings,
  ShieldCheck,
  Users,
  X
} from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { Logo } from '../common/Logo';
import { useAuth } from '../../contexts/AuthContext';
import type { Role } from '../../types/auth';

const ALL: Role[] = ['ADMIN', 'INVESTIGATOR', 'REVIEWER'];

const sections: { title: string; items: { label: string; path: string; icon: typeof LayoutDashboard; roles: Role[] }[] }[] = [
  { title: 'Overview', items: [{ label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard, roles: ALL }] },
  {
    title: 'Investigation',
    items: [
      { label: 'Cases', path: '/cases', icon: ClipboardList, roles: ALL },
      { label: 'Devices', path: '/devices', icon: HardDrive, roles: ALL },
      { label: 'Evidence search', path: '/evidence', icon: Database, roles: ALL },
      { label: 'Timeline', path: '/timeline', icon: History, roles: ALL },
      { label: 'Relationships', path: '/relationships', icon: GitBranch, roles: ALL },
      { label: 'Assistant', path: '/assistant', icon: Bot, roles: ALL }
    ]
  },
  {
    title: 'Output',
    items: [
      { label: 'Reports', path: '/reports', icon: FileText, roles: ALL },
      { label: 'Processing', path: '/processing', icon: Activity, roles: ALL }
    ]
  },
  {
    title: 'Administration',
    items: [
      { label: 'Audit log', path: '/audit', icon: ShieldCheck, roles: ['ADMIN'] },
      { label: 'Users', path: '/users', icon: Users, roles: ['ADMIN'] },
      { label: 'Settings', path: '/settings', icon: Settings, roles: ['ADMIN'] }
    ]
  }
];

export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { hasRole } = useAuth();
  const nav = (
    <nav aria-label="Main" className="flex-1 space-y-5 overflow-y-auto px-3 py-4">
      {sections.map((section) => {
        const items = section.items.filter((item) => hasRole(item.roles));
        if (items.length === 0) return null;
        return (
          <div key={section.title}>
            <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{section.title}</p>
            <ul className="space-y-0.5">
              {items.map(({ label, path, icon: Icon }) => (
                <li key={path}>
                  <NavLink
                    to={path}
                    onClick={onClose}
                    className={({ isActive }) =>
                      `flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors ${
                        isActive ? 'bg-brand-50 font-medium text-brand-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4 shrink-0" aria-hidden />
                    {label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </nav>
  );

  return (
    <>
      <aside className="no-print fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-slate-200 bg-white lg:flex">
        <div className="flex h-14 items-center border-b border-slate-200 px-4">
          <Logo />
        </div>
        {nav}
      </aside>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-slate-900/40" onClick={onClose} />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col bg-white shadow-overlay">
            <div className="flex h-14 items-center justify-between border-b border-slate-200 px-4">
              <Logo />
              <button type="button" aria-label="Close navigation" onClick={onClose} className="rounded p-1 text-slate-500 hover:bg-slate-100">
                <X className="h-5 w-5" />
              </button>
            </div>
            {nav}
          </aside>
        </div>
      )}
    </>
  );
}
