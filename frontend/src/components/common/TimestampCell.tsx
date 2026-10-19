import { formatUtc, TIMESTAMP_LABELS } from '../../lib/format';
import type { TimestampStatus } from '../../types/api';
import { StatusBadge } from '../ui/Badge';

/** Normalized UTC time plus the original source text and how the time was derived. */
export function TimestampCell({ eventTime, originalTimestamp, originalTimezone, status, compact = false }: {
  eventTime: string | null;
  originalTimestamp: string | null;
  originalTimezone: string | null;
  status: TimestampStatus;
  compact?: boolean;
}) {
  const showOriginal = originalTimestamp && originalTimestamp !== eventTime;
  return (
    <div className="min-w-[150px]">
      <div className="whitespace-nowrap text-sm text-slate-900">{eventTime ? formatUtc(eventTime) : 'Undated'}</div>
      {!compact && showOriginal && (
        <div className="mono mt-0.5 text-slate-500" title="Original timestamp as it appears in the source file">
          src: {originalTimestamp}
          {originalTimezone ? ` (${originalTimezone})` : ''}
        </div>
      )}
      {status !== 'EXACT' && (
        <div className="mt-1">
          <StatusBadge value={status} label={TIMESTAMP_LABELS[status]} />
        </div>
      )}
    </div>
  );
}
