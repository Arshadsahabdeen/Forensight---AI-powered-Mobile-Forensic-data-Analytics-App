import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, Menu, Search } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { humanize, initials } from '../../lib/format';

export function Topbar({ onMenu }: { onMenu: () => void }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const onClick = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setMenuOpen(false);
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const term = query.trim();
    if (term) navigate(`/evidence?search=${encodeURIComponent(term)}`);
  }

  return (
    <header className="no-print sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur lg:px-6">
      <button type="button" onClick={onMenu} aria-label="Open navigation" className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100 lg:hidden">
        <Menu className="h-5 w-5" />
      </button>
      <form onSubmit={submit} role="search" className="relative max-w-md flex-1">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search evidence (text, number, contact)…"
          aria-label="Search evidence across accessible cases"
          className="input h-9 bg-slate-50 pl-9"
        />
      </form>
      <div className="ml-auto" ref={menuRef}>
        <button
          type="button"
          onClick={() => setMenuOpen((value) => !value)}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-slate-100"
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-800">{initials(user?.fullName)}</span>
          <span className="hidden text-left sm:block">
            <span className="block text-sm font-medium leading-tight text-slate-900">{user?.fullName}</span>
            <span className="block text-xs leading-tight text-slate-500">{humanize(user?.roles[0])}</span>
          </span>
          <ChevronDown className="h-4 w-4 text-slate-400" aria-hidden />
        </button>
        {menuOpen && (
          <div role="menu" className="absolute right-4 top-12 w-60 rounded-md border border-slate-200 bg-white py-1 shadow-overlay">
            <div className="border-b border-slate-100 px-3 py-2">
              <p className="text-sm font-medium text-slate-900">{user?.fullName}</p>
              <p className="truncate text-xs text-slate-500">{user?.email}</p>
              <p className="mt-1 text-xs text-slate-500">Signed in as @{user?.username}</p>
            </div>
            <button type="button" role="menuitem" onClick={() => void logout()}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50">
              <LogOut className="h-4 w-4" aria-hidden />
              Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
