import { Fingerprint } from 'lucide-react';

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-700 text-white">
        <Fingerprint className="h-4 w-4" aria-hidden />
      </div>
      {!compact && (
        <div className="leading-tight">
          <div className="text-sm font-semibold text-slate-900">Forensight</div>
          <div className="text-[11px] text-slate-500">Evidence intelligence</div>
        </div>
      )}
    </div>
  );
}
