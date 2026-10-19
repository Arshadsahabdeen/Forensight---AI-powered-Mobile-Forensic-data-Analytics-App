import { useEffect, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Database, FilterX } from 'lucide-react';
import { getEvidenceFilters, searchEvidence } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useDebounce } from '../../hooks/useDebounce';
import { Card } from '../../components/ui/Card';
import { Field, Input, SearchInput, Select } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Pagination, SortableTh, Table, Td, Th, Tr } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { TimestampCell } from '../../components/common/TimestampCell';
import { CasePicker } from '../../components/common/CasePicker';
import { describeParticipants, humanize, TIMESTAMP_LABELS, utcInputToIso } from '../../lib/format';
import { EvidenceDrawer } from './EvidenceDrawer';
import type { EvidenceQuery } from '../../types/api';

type SortField = EvidenceQuery['sort'];

export interface EvidenceFilters {
  search: string;
  entity: string;
  artifactType: string;
  category: string;
  timestampStatus: string;
  from: string;
  to: string;
  caseId?: number;
}

export const EMPTY_FILTERS: EvidenceFilters = { search: '', entity: '', artifactType: '', category: '', timestampStatus: '', from: '', to: '' };

/** Server-side paginated evidence search with filters and a detail drawer. Scope props fix the case/device/file. */
export function EvidenceBrowser({ caseId, deviceId, fileId, initialFilters, showCaseFilter = false }: {
  caseId?: number;
  deviceId?: number;
  fileId?: number;
  initialFilters?: Partial<EvidenceFilters>;
  showCaseFilter?: boolean;
}) {
  const [filters, setFilters] = useState<EvidenceFilters>({ ...EMPTY_FILTERS, ...initialFilters });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sort, setSort] = useState<SortField>('eventTime');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [selected, setSelected] = useState<number | null>(null);

  useEffect(() => {
    if (initialFilters) setFilters((current) => ({ ...current, ...initialFilters }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialFilters?.search, initialFilters?.caseId]);

  const search = useDebounce(filters.search);
  const entity = useDebounce(filters.entity);
  const options = useQuery({ queryKey: ['evidence-filters'], queryFn: getEvidenceFilters, staleTime: 5 * 60_000 });

  const query: EvidenceQuery = {
    page,
    size,
    sort,
    direction,
    search,
    entity,
    artifactType: filters.artifactType,
    category: filters.category,
    timestampStatus: filters.timestampStatus,
    startTime: utcInputToIso(filters.from),
    endTime: utcInputToIso(filters.to),
    caseId: caseId ?? filters.caseId,
    deviceId,
    fileId
  };
  const invalidRange = Boolean(filters.from && filters.to && filters.from > filters.to);
  const results = useQuery({
    queryKey: ['evidence-search', query],
    queryFn: () => searchEvidence(query),
    placeholderData: keepPreviousData,
    enabled: !invalidRange
  });

  function update<K extends keyof EvidenceFilters>(key: K, value: EvidenceFilters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(0);
  }

  function toggleSort(field: SortField) {
    if (field === sort) setDirection((value) => (value === 'asc' ? 'desc' : 'asc'));
    else {
      setSort(field);
      setDirection(field === 'eventTime' ? 'desc' : 'asc');
    }
    setPage(0);
  }

  const active = Object.entries(filters).some(([key, value]) => key !== 'caseId' && value) || (showCaseFilter && filters.caseId);

  return (
    <Card>
      <div className="space-y-3 border-b border-slate-200 p-4">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <SearchInput aria-label="Search text" placeholder="Search text, titles, file names…" value={filters.search}
            onChange={(event) => update('search', event.target.value)} />
          <Input aria-label="Identifier" placeholder="Phone, account or contact name" value={filters.entity}
            onChange={(event) => update('entity', event.target.value)} />
          {showCaseFilter && <CasePicker allowAll value={filters.caseId} onChange={(value) => update('caseId', value)} />}
          <Select aria-label="Category" value={filters.category} onChange={(event) => update('category', event.target.value)}>
            <option value="">All categories</option>
            {options.data?.categories.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
          </Select>
          <Select aria-label="Artifact type" value={filters.artifactType} onChange={(event) => update('artifactType', event.target.value)}>
            <option value="">All artifact types</option>
            {options.data?.artifactTypes.map((value) => <option key={value} value={value}>{value}</option>)}
          </Select>
          <Select aria-label="Timestamp quality" value={filters.timestampStatus} onChange={(event) => update('timestampStatus', event.target.value)}>
            <option value="">Any timestamp quality</option>
            {options.data?.timestampStatuses.map((value) => <option key={value} value={value}>{TIMESTAMP_LABELS[value] ?? value}</option>)}
          </Select>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <Field label="From (UTC)" className="w-full sm:w-56" error={invalidRange ? 'Must be before the end time.' : undefined}>
            {(props) => <Input {...props} type="datetime-local" value={filters.from} onChange={(event) => update('from', event.target.value)} />}
          </Field>
          <Field label="To (UTC)" className="w-full sm:w-56">
            {(props) => <Input {...props} type="datetime-local" value={filters.to} onChange={(event) => update('to', event.target.value)} />}
          </Field>
          {active && (
            <Button variant="ghost" size="sm" icon={<FilterX className="h-4 w-4" />}
              onClick={() => { setFilters({ ...EMPTY_FILTERS }); setPage(0); }}>
              Clear filters
            </Button>
          )}
        </div>
      </div>

      {results.isLoading ? (
        <SkeletonRows rows={8} columns={5} />
      ) : results.isError ? (
        <ErrorState message={friendlyError(results.error)} onRetry={() => void results.refetch()} />
      ) : results.data && results.data.items.length === 0 ? (
        <EmptyState icon={<Database className="h-6 w-6" />} title="No evidence records found"
          description={active ? 'No records match these filters.' : 'Import an extraction file into a case to create evidence records.'} />
      ) : results.data ? (
        <>
          <Table minWidth={900}>
            <thead>
              <tr>
                <SortableTh label="Time (UTC)" field="eventTime" sort={sort} direction={direction} onSort={toggleSort} />
                <SortableTh label="Type" field="artifactType" sort={sort} direction={direction} onSort={toggleSort} />
                <Th>Content</Th>
                <Th>Participants / location</Th>
                <Th>{caseId ? 'Device' : 'Case / device'}</Th>
                <SortableTh label="ID" field="id" sort={sort} direction={direction} onSort={toggleSort} className="text-right" />
              </tr>
            </thead>
            <tbody className={results.isFetching ? 'opacity-60' : ''}>
              {results.data.items.map((item) => (
                <Tr key={item.id} onClick={() => setSelected(item.id)} selected={selected === item.id}>
                  <Td><TimestampCell eventTime={item.eventTime} originalTimestamp={item.originalTimestamp} originalTimezone={item.originalTimezone} status={item.timestampStatus} compact /></Td>
                  <Td><Badge>{item.artifactType ?? 'Unknown'}</Badge></Td>
                  <Td className="max-w-md">
                    {item.title && <p className="font-medium text-slate-900">{item.title}</p>}
                    <p className="line-clamp-2 text-slate-600">{item.content ?? (item.title ? '' : '—')}</p>
                  </Td>
                  <Td className="text-xs">
                    <p>{describeParticipants(item) || '—'}</p>
                    {item.location && <p className="text-slate-500">{item.location}</p>}
                  </Td>
                  <Td className="text-xs">
                    {!caseId && <p className="font-medium text-slate-900">{item.caseNumber}</p>}
                    <p className="text-slate-500">{item.deviceName}</p>
                  </Td>
                  <Td className="text-right mono text-slate-500">#{item.id}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          <Pagination page={results.data.page} totalPages={results.data.totalPages} totalElements={results.data.totalElements} size={size}
            onPage={setPage} onSize={(value) => { setSize(value); setPage(0); }} />
        </>
      ) : null}
      <EvidenceDrawer evidenceId={selected} onClose={() => setSelected(null)} onSelect={setSelected} />
    </Card>
  );
}
