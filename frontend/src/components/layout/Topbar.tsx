import { Bell, LogOut, Menu, Search, Settings, UserCircle } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';

export function Topbar({ collapsed, toggle }: { collapsed: boolean; toggle: () => void }) {
  const { user, logout } = useAuth();
  const initials = user?.fullName
    ?.split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2) ?? 'U';
  return (
    <header className={`fixed right-0 top-0 z-20 flex h-16 items-center justify-between border-b border-slate-800 bg-[#0b1117]/86 px-5 backdrop-blur transition-all ${collapsed ? 'left-20' : 'left-72'}`}>
      <div className="flex items-center gap-3">
        <button onClick={toggle} className="rounded-md border border-slate-700 p-2 text-slate-300 hover:bg-white/5" aria-label="Toggle sidebar">
          <Menu className="h-5 w-5" />
        </button>
        <div className="hidden items-center gap-2 rounded-md border border-slate-800 bg-[#101820] px-3 py-2 md:flex">
          <Search className="h-4 w-4 text-slate-500" />
          <input className="w-80 bg-transparent text-sm outline-none placeholder:text-slate-500" placeholder="Search cases, artifacts, hashes..." />
        </div>
      </div>
      <div className="flex items-center gap-3">
        <button className="rounded-md border border-slate-800 p-2 text-slate-300 hover:bg-white/5" aria-label="Notifications">
          <Bell className="h-5 w-5" />
        </button>
        <div className="group relative">
        <button className="flex items-center gap-3 rounded-md border border-slate-800 bg-[#101820] px-3 py-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-signal/20 text-sm font-semibold text-signal">
            {initials}
          </div>
          <div className="hidden text-left sm:block">
            <div className="text-sm font-semibold text-slate-100">{user?.fullName}</div>
            <div className="text-xs text-slate-500">@{user?.username} - {user?.roles[0]}</div>
          </div>
        </button>
        <div className="invisible absolute right-0 top-12 w-52 rounded-md border border-slate-800 bg-[#101820] p-2 opacity-0 shadow-xl transition group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100">
          <button className="flex w-full items-center gap-2 rounded px-3 py-2 text-left text-sm text-slate-300 hover:bg-white/5">
            <UserCircle className="h-4 w-4" /> Profile
          </button>
          <button className="flex w-full items-center gap-2 rounded px-3 py-2 text-left text-sm text-slate-300 hover:bg-white/5">
            <Settings className="h-4 w-4" /> Settings
          </button>
          <button onClick={logout} className="flex w-full items-center gap-2 rounded px-3 py-2 text-left text-sm text-red-200 hover:bg-red-500/10">
            <LogOut className="h-4 w-4" /> Logout
          </button>
        </div>
        </div>
      </div>
    </header>
  );
}
