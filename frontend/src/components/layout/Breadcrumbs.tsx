import { ChevronRight, Home } from 'lucide-react';
import { useLocation } from 'react-router-dom';

export function Breadcrumbs() {
  const location = useLocation();
  const parts = location.pathname.split('/').filter(Boolean);
  return (
    <div className="mb-5 flex items-center gap-2 text-sm text-slate-500">
      <Home className="h-4 w-4" />
      {parts.map((part) => (
        <span key={part} className="flex items-center gap-2">
          <ChevronRight className="h-4 w-4" />
          <span className="capitalize text-slate-300">{part.replace('-', ' ')}</span>
        </span>
      ))}
    </div>
  );
}
