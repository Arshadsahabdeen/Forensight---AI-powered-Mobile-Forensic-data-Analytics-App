import type { ReactNode } from 'react';
import { humanize } from '../../lib/format';

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'brand';

const tones: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-700 ring-slate-200',
  info: 'bg-sky-50 text-sky-800 ring-sky-200',
  success: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  warning: 'bg-amber-50 text-amber-800 ring-amber-200',
  danger: 'bg-red-50 text-red-800 ring-red-200',
  brand: 'bg-brand-50 text-brand-800 ring-brand-200'
};

export function Badge({ tone = 'neutral', children, title }: { tone?: Tone; children: ReactNode; title?: string }) {
  return (
    <span title={title} className={`inline-flex items-center gap-1 whitespace-nowrap rounded px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>
      {children}
    </span>
  );
}

const STATUS_TONES: Record<string, Tone> = {
  // cases
  OPEN: 'info', ACTIVE: 'brand', ON_HOLD: 'warning', CLOSED: 'neutral', ARCHIVED: 'neutral',
  // priority
  LOW: 'neutral', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
  // jobs / files
  QUEUED: 'neutral', RUNNING: 'info', SUCCEEDED: 'success', FAILED: 'danger',
  RECEIVED: 'neutral', PROCESSING: 'info', PROCESSED: 'success',
  NOT_VERIFIED: 'neutral', VERIFIED: 'success', MISMATCH: 'danger', MISSING: 'danger',
  // timestamps
  EXACT: 'success', ASSUMED_UTC: 'warning', DATE_ONLY: 'warning', UNPARSEABLE: 'danger', LEGACY_IMPORT: 'neutral',
  // findings / reports
  DRAFT: 'neutral', NEEDS_REVIEW: 'warning', REVIEWED: 'success', REJECTED: 'danger', GENERATED: 'info',
  VERIFIED_FACT: 'success', INFERENCE: 'warning', AI_SUGGESTED: 'brand', VERIFIED_RECORD: 'success', INFERRED: 'warning',
  // devices
  SEIZED: 'neutral', ACQUIRED: 'info', WAITING_FOR_EXTRACTION: 'warning', EXTRACTION_COMPLETED: 'success', UNDER_ANALYSIS: 'brand', REPORT_GENERATED: 'success'
};

export function StatusBadge({ value, label, title }: { value: string | null | undefined; label?: string; title?: string }) {
  if (!value) return <span className="text-slate-400">—</span>;
  return (
    <Badge tone={STATUS_TONES[value] ?? 'neutral'} title={title}>
      {label ?? humanize(value)}
    </Badge>
  );
}
