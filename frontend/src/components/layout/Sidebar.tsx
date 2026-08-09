import {
  BarChart3,
  Bot,
  ClipboardList,
  Database,
  FileClock,
  FileText,
  HardDrive,
  LayoutDashboard,
  LogOut,
  Settings,
  ShieldCheck,
  Users
} from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { Logo } from '../common/Logo';
import { useAuth } from '../../contexts/AuthContext';

const items = [
  { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard, roles: ['ADMIN', 'INVESTIGATOR', 'REVIEWER'] },
  { label: 'Cases', path: '/cases', icon: ClipboardList, roles: ['ADMIN', 'INVESTIGATOR', 'REVIEWER'] },
  { label: 'Devices', path: '/devices', icon: HardDrive, roles: ['ADMIN', 'INVESTIGATOR'] },
  { label: 'Evidence', path: '/evidence', icon: Database, roles: ['ADMIN', 'INVESTIGATOR', 'REVIEWER'] },
  { label: 'Timeline', path: '/timeline', icon: FileClock, roles: ['ADMIN', 'INVESTIGATOR', 'REVIEWER'] },
  { label: 'Reports', path: '/reports', icon: FileText, roles: ['ADMIN', 'INVESTIGATOR', 'REVIEWER'] },
  { label: 'Audit Logs', path: '/audit', icon: ShieldCheck, roles: ['ADMIN'] },
  { label: 'AI Analysis', path: '/ai', icon: Bot, roles: ['ADMIN', 'INVESTIGATOR'] },
  { label: 'Users', path: '/users', icon: Users, roles: ['ADMIN'] },
  { label: 'Settings', path: '/settings', icon: Settings, roles: ['ADMIN'] }
];

export function Sidebar({ collapsed }: { collapsed: boolean }) {
  const { logout, hasRole } = useAuth();
  const visibleItems = items.filter((item) => hasRole(item.roles));
  return (
    <aside className={`fixed inset-y-0 left-0 z-30 border-r border-slate-800 bg-[#0c131a]/95 backdrop-blur transition-all ${collapsed ? 'w-20' : 'w-72'}`}>
      <div className="flex h-16 items-center border-b border-slate-800 px-5">
        <Logo compact={collapsed} />
      </div>
      <nav className="flex h-[calc(100%-4rem)] flex-col justify-between p-3">
        <div className="space-y-1">
          {visibleItems.map(({ label, path, icon: Icon }) => (
            <NavLink
              key={path}
              to={path}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-md px-3 py-2.5 text-sm transition ${isActive ? 'bg-cyanline/14 text-cyanline' : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'}`
              }
            >
              <Icon className="h-5 w-5 shrink-0" />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </div>
        <button onClick={logout} className="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm text-slate-400 transition hover:bg-red-500/10 hover:text-red-200">
          <LogOut className="h-5 w-5" />
          {!collapsed && <span>Logout</span>}
        </button>
      </nav>
    </aside>
  );
}
