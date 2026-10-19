import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Check, ChevronLeft, Copy } from 'lucide-react';

export function PageHeader({ title, description, actions, back, meta }: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  back?: { to: string; label: string };
  meta?: ReactNode;
}) {
  return (
    <div className="mb-6">
      {back && (
        <Link to={back.to} className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-900">
          <ChevronLeft className="h-4 w-4" aria-hidden />
          {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold text-slate-900 sm:text-2xl">{title}</h1>
          {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
          {meta && <div className="mt-2 flex flex-wrap items-center gap-2">{meta}</div>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}

/** Full hash in monospace with a copy button; truncates visually on small screens. */
export function HashValue({ value, label = 'SHA-256' }: { value: string | null | undefined; label?: string }) {
  const [copied, setCopied] = useState(false);
  if (!value) return <span className="text-slate-400">Not available</span>;
  async function copy() {
    try {
      await navigator.clipboard.writeText(value!);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  }
  return (
    <span className="inline-flex max-w-full items-center gap-1.5">
      <code className="mono truncate rounded bg-slate-100 px-1.5 py-0.5 text-slate-800" title={value}>{value}</code>
      <button type="button" onClick={copy} aria-label={`Copy ${label}`} title={`Copy ${label}`} className="shrink-0 rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
        {copied ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
      </button>
    </span>
  );
}
