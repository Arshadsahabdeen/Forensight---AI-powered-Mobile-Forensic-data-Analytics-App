import { Fingerprint } from 'lucide-react';

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex h-10 w-10 items-center justify-center rounded-md border border-cyanline/40 bg-cyanline/10">
        <Fingerprint className="h-5 w-5 text-cyanline" />
      </div>
      {!compact && (
        <div>
          <div className="text-sm font-bold tracking-wide text-white">ForensicIQ</div>
          <div className="text-xs text-slate-400">Evidence Intelligence</div>
        </div>
      )}
    </div>
  );
}
