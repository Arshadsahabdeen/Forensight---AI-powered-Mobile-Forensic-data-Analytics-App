import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { AlertTriangle, History, Info } from 'lucide-react';
import { getTimeline } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useDebounce } from '../../hooks/useDebounce';
import { Card, CardHeader } from '../../components/ui/Card';
import { Checkbox, Field, Input, Select } from '../../components/ui/Input';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Pagination } from '../../components/ui/Table';
import { describeParticipants, formatUtc, humanize, TIMESTAMP_LABELS, utcInputToIso } from '../../lib/format';
import { EvidenceDrawer } from '../evidence/EvidenceDrawer';
import { formatDayHeading, groupByUtcDay, utcTimeOfDay } from './timelineUtils';
import type { CaseDetail, TimelineEvent } from '../../types/api';

const CATEGORIES = ['COMMUNICATION', 'LOCATION', 'CONTACT', 'MEDIA', 'BROWSER', 'ACCOUNT', 'OTHER'];

export function TimelinePanel({ caseId, devices, initialDeviceId }: {
  caseId: number;
  devices: Pick<CaseDetail['devices'][number], 'id' | 'deviceName'>[];
  initialDeviceId?: number;
}) {
  const [deviceId, setDeviceId] = useState(initialDeviceId ? String(initialDeviceId) : '');
  const [category, setCategory] = useState('');
  const [entity, setEntity] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [flaggedOnly, setFlaggedOnly] = useState(false);
  const [order, setOrder] = useState<'asc' | 'desc'>('asc');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(100);
  const [selected, setSelected] = useState<number | null>(null);
  const debouncedEntity = useDebounce(entity);
  const invalidRange = Boolean(from && to && from > to);

  const params = {
    caseId,
    deviceId: deviceId ? Number(deviceId) : undefined,
    category,
    entity: debouncedEntity,
    from: utcInputToIso(from),
    to: utcInputToIso(to),
    flaggedOnly,
    order,
    page,
    size
  };
  const timeline = useQuery({ queryKey: ['timeline', params], queryFn: () => getTimeline(params), placeholderData: keepPreviousData, enabled: !invalidRange });
  const data = timeline.data;
  const reset = () => setPage(0);

  return (
    <div className="space-y-4">
      <Card>
        <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-6">
          <Field label="Device">
            {(props) => (
              <Select {...props} value={deviceId} onChange={(event) => { setDeviceId(event.target.value); reset(); }}>
                <option value="">All devices</option>
                {devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
              </Select>
            )}
          </Field>
          <Field label="Event type">
            {(props) => (
              <Select {...props} value={category} onChange={(event) => { setCategory(event.target.value); reset(); }}>
                <option value="">All types</option>
                {CATEGORIES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
              </Select>
            )}
          </Field>
          <Field label="Entity">
            {(props) => <Input {...props} placeholder="Number, name, place" value={entity} onChange={(event) => { setEntity(event.target.value); reset(); }} />}
          </Field>
          <Field label="From (UTC)" error={invalidRange ? 'Must be before the end.' : undefined}>
            {(props) => <Input {...props} type="datetime-local" value={from} onChange={(event) => { setFrom(event.target.value); reset(); }} />}
          </Field>
          <Field label="To (UTC)">
            {(props) => <Input {...props} type="datetime-local" value={to} onChange={(event) => { setTo(event.target.value); reset(); }} />}
          </Field>
          <Field label="Order">
            {(props) => (
              <Select {...props} value={order} onChange={(event) => { setOrder(event.target.value as 'asc' | 'desc'); reset(); }}>
                <option value="asc">Oldest first</option>
                <option value="desc">Newest first</option>
              </Select>
            )}
          </Field>
        </div>
        <div className="border-t border-slate-200 px-4 py-3">
          <Checkbox label="Only events with timestamp warnings or cross-device observations" checked={flaggedOnly}
            onChange={(event) => { setFlaggedOnly(event.target.checked); reset(); }} />
        </div>
      </Card>

      {data && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
          <span><strong className="text-slate-900">{data.totalDated}</strong> dated event(s)</span>
          {data.firstEventTime && <span>{formatUtc(data.firstEventTime)} → {formatUtc(data.lastEventTime)}</span>}
          <span><strong className="text-slate-900">{data.totalUndated}</strong> undated</span>
          <span><strong className="text-slate-900">{data.flaggedEvents}</strong> flagged</span>
          {Object.entries(data.timestampStatusCounts).map(([status, count]) => (
            <span key={status}>{TIMESTAMP_LABELS[status] ?? status}: {count}</span>
          ))}
        </div>
      )}
      {data && <p className="flex items-start gap-1.5 text-xs text-slate-500"><Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />{data.timezoneNote}</p>}

      <Card>
        {timeline.isLoading ? (
          <SkeletonRows rows={8} columns={3} />
        ) : timeline.isError ? (
          <ErrorState message={friendlyError(timeline.error)} onRetry={() => void timeline.refetch()} />
        ) : data && data.events.length === 0 ? (
          <EmptyState icon={<History className="h-6 w-6" />} title="No dated events match"
            description={data.totalUndated > 0 ? 'Some records have no usable timestamp; they are listed below.' : 'Import evidence or widen the filters.'} />
        ) : data ? (
          <>
            <div className={timeline.isFetching ? 'opacity-60' : ''}>
              {groupByUtcDay(data.events).map((group) => (
                <section key={group.day}>
                  <h3 className="sticky top-14 z-[1] border-y border-slate-200 bg-slate-50 px-5 py-1.5 text-xs font-semibold text-slate-600">
                    {formatDayHeading(group.day)} <span className="font-normal text-slate-400">(UTC)</span>
                  </h3>
                  <ol>
                    {group.events.map((event) => <EventRow key={event.evidenceId} event={event} onOpen={setSelected} />)}
                  </ol>
                </section>
              ))}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalDated} size={size}
              onPage={setPage} onSize={(value) => { setSize(value); setPage(0); }} />
          </>
        ) : null}
      </Card>

      {data && data.undated.length > 0 && (
        <Card>
          <CardHeader title={`Undated records (${data.totalUndated})`} description="Missing or unparseable timestamps. These cannot be placed in time; original text is preserved." />
          <ol>{data.undated.map((event) => <EventRow key={event.evidenceId} event={event} onOpen={setSelected} />)}</ol>
        </Card>
      )}
      <EvidenceDrawer evidenceId={selected} onClose={() => setSelected(null)} onSelect={setSelected} />
    </div>
  );
}

function EventRow({ event, onOpen }: { event: TimelineEvent; onOpen: (id: number) => void }) {
  const warnings = event.flags.filter((flag) => flag.severity === 'WARNING');
  const infos = event.flags.filter((flag) => flag.severity === 'INFO' && flag.code !== 'MISSING_TIMESTAMP');
  return (
    <li className="border-b border-slate-100 last:border-0">
      <button type="button" onClick={() => onOpen(event.evidenceId)} className="grid w-full gap-3 px-5 py-3 text-left hover:bg-slate-50 sm:grid-cols-[110px_1fr]">
        <div>
          <p className="mono text-sm text-slate-900">{utcTimeOfDay(event.eventTime)}</p>
          {event.originalTimestamp && event.timestampStatus !== 'EXACT' && (
            <p className="mono mt-0.5 truncate text-slate-400" title={`Original: ${event.originalTimestamp}`}>{event.originalTimestamp}</p>
          )}
        </div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <Badge>{event.artifactType ?? 'Unknown'}</Badge>
            {event.title && <span className="text-sm font-medium text-slate-900">{event.title}</span>}
            <span className="text-xs text-slate-500">{event.deviceName}</span>
            {event.timestampStatus !== 'EXACT' && <StatusBadge value={event.timestampStatus} label={TIMESTAMP_LABELS[event.timestampStatus]} />}
            <span className="mono ml-auto text-slate-400">#{event.evidenceId}</span>
          </div>
          {event.summary && <p className="mt-1 line-clamp-2 text-sm text-slate-700">{event.summary}</p>}
          <p className="mt-0.5 text-xs text-slate-500">
            {describeParticipants(event)}
            {event.location && <>{describeParticipants(event) ? ' · ' : ''}{event.location}</>}
          </p>
          {(warnings.length > 0 || infos.length > 0) && (
            <ul className="mt-1.5 space-y-0.5">
              {warnings.map((flag) => (
                <li key={flag.code} className="flex items-start gap-1 text-xs text-amber-800"><AlertTriangle className="mt-0.5 h-3 w-3 shrink-0" />{flag.message}</li>
              ))}
              {infos.map((flag) => (
                <li key={flag.code} className="flex items-start gap-1 text-xs text-slate-500"><Info className="mt-0.5 h-3 w-3 shrink-0" />{flag.message}
                  {flag.relatedEvidenceIds.length > 0 && <span className="ml-1">(records {flag.relatedEvidenceIds.map((id) => `#${id}`).join(', ')})</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      </button>
    </li>
  );
}
