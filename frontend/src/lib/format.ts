/** Formatting helpers. Investigation times are shown in UTC to match normalized evidence times and reports. */

const UTC_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'UTC',
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
});

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', { year: 'numeric', month: 'short', day: '2-digit', timeZone: 'UTC' });

export function formatUtc(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${UTC_FORMAT.format(date)} UTC`;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value.length === 10 ? `${value}T00:00:00Z` : value);
  return Number.isNaN(date.getTime()) ? value : DATE_FORMAT.format(date);
}

export function formatRelative(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) return '—';
  const date = new Date(value);
  const seconds = Math.round((now.getTime() - date.getTime()) / 1000);
  if (Number.isNaN(seconds)) return value;
  const abs = Math.abs(seconds);
  const suffix = seconds >= 0 ? 'ago' : 'from now';
  if (abs < 60) return 'just now';
  if (abs < 3600) return `${Math.floor(abs / 60)} min ${suffix}`;
  if (abs < 86400) return `${Math.floor(abs / 3600)} h ${suffix}`;
  if (abs < 86400 * 30) return `${Math.floor(abs / 86400)} d ${suffix}`;
  return formatDate(value);
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

/** Converts an <input type="datetime-local"> value (interpreted as UTC) to an ISO instant. */
export function utcInputToIso(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}:00Z`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

export function humanize(value: string | null | undefined): string {
  if (!value) return '—';
  return value
    .toLowerCase()
    .split('_')
    .map((part, index) => (index === 0 ? part.charAt(0).toUpperCase() + part.slice(1) : part))
    .join(' ');
}

export function shortHash(hash: string | null | undefined, length = 12): string {
  if (!hash) return '—';
  return hash.length > length ? `${hash.slice(0, length)}…` : hash;
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

export const TIMESTAMP_LABELS: Record<string, string> = {
  EXACT: 'Exact (zone known)',
  ASSUMED_UTC: 'Assumed UTC',
  DATE_ONLY: 'Date only',
  MISSING: 'No timestamp',
  UNPARSEABLE: 'Unparseable',
  LEGACY_IMPORT: 'Legacy import'
};

export const BASIS_LABELS: Record<string, string> = {
  VERIFIED_FACT: 'Verified fact',
  INFERENCE: 'Inference',
  AI_SUGGESTED: 'AI-suggested',
  VERIFIED_RECORD: 'Stated by record',
  INFERRED: 'Inferred'
};

export function describeParticipants(item: { source?: string | null; target?: string | null; phoneNumber?: string | null; contactName?: string | null }): string {
  const parts: string[] = [];
  if (item.source || item.target) parts.push(`${item.source ?? '?'} → ${item.target ?? '?'}`);
  else if (item.phoneNumber) parts.push(item.phoneNumber);
  if (item.contactName) parts.push(item.contactName);
  return parts.join(' · ');
}
