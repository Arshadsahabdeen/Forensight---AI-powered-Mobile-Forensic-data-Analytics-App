import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, RefreshCw, RotateCcw } from 'lucide-react';
import { getJob, getJobs, retryJob } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, KeyValueList, Stat } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Input';
import { StatusBadge } from '../../components/ui/Badge';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Pagination, Table, Td, Th, Tr } from '../../components/ui/Table';
import { Drawer } from '../../components/ui/Dialog';
import { CasePicker } from '../../components/common/CasePicker';
import { formatRelative, formatUtc } from '../../lib/format';
import type { ProcessingJob } from '../../types/api';

export default function ProcessingPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { canWrite } = usePermissions();
  const [status, setStatus] = useState('');
  const [caseId, setCaseId] = useState<number | undefined>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<number | null>(null);

  const params = { status, caseId, page, size };
  const jobs = useQuery({
    queryKey: ['jobs', params],
    queryFn: () => getJobs(params),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => ((query.state.data?.running ?? 0) + (query.state.data?.queued ?? 0) > 0 ? 3000 : false)
  });
  const retry = useMutation({
    mutationFn: (id: number) => retryJob(id),
    onSuccess: (job) => {
      if (job.status === 'FAILED') toast.error(`Retry failed: ${job.errorMessage ?? 'see log'}`);
      else toast.success('Job re-queued from the stored original file.');
      void queryClient.invalidateQueries({ queryKey: ['jobs'] });
      void queryClient.invalidateQueries({ queryKey: ['job'] });
      void queryClient.invalidateQueries({ queryKey: ['evidence-files'] });
    },
    onError: (error) => toast.error(friendlyError(error))
  });

  return (
    <>
      <PageHeader title="Processing monitor" description="Import jobs for uploaded evidence files. Failed imports leave no partial records and can be retried."
        actions={<Button variant="secondary" icon={<RefreshCw className="h-4 w-4" />} onClick={() => void jobs.refetch()} loading={jobs.isFetching && !jobs.isLoading}>Refresh</Button>} />
      {jobs.data && (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <Stat label="Queued" value={jobs.data.queued} />
          <Stat label="Running" value={jobs.data.running} />
          <Stat label="Failed" value={jobs.data.failed} tone={jobs.data.failed > 0 ? 'danger' : 'default'} />
        </div>
      )}
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-[280px_200px]">
          <CasePicker allowAll value={caseId} onChange={(value) => { setCaseId(value); setPage(0); }} />
          <Select aria-label="Status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            <option value="QUEUED">Queued</option>
            <option value="RUNNING">Running</option>
            <option value="SUCCEEDED">Succeeded</option>
            <option value="FAILED">Failed</option>
          </Select>
        </div>
        {jobs.isLoading ? <SkeletonRows rows={6} columns={5} /> : jobs.isError ? (
          <ErrorState message={friendlyError(jobs.error)} onRetry={() => void jobs.refetch()} />
        ) : jobs.data && jobs.data.items.length === 0 ? (
          <EmptyState icon={<Activity className="h-6 w-6" />} title="No processing jobs" description="Jobs appear when evidence files are uploaded to a case." />
        ) : jobs.data ? (
          <>
            <Table minWidth={880}>
              <thead><tr><Th>Job</Th><Th>File</Th><Th>Case / device</Th><Th>Status</Th><Th>Progress</Th><Th>Started</Th><Th><span className="sr-only">Actions</span></Th></tr></thead>
              <tbody>
                {jobs.data.items.map((job) => (
                  <Tr key={job.id} onClick={() => setSelected(job.id)}>
                    <Td className="mono">#{job.id}<p className="text-slate-400">attempt {job.attempts}</p></Td>
                    <Td className="max-w-[220px]"><p className="truncate">{job.fileName ?? '—'}</p><p className="text-xs text-slate-500">by {job.createdBy}</p></Td>
                    <Td className="text-xs"><Link to={`/cases/${job.caseId}?tab=files`} className="link" onClick={(event) => event.stopPropagation()}>{job.caseNumber}</Link><p className="text-slate-500">{job.deviceName ?? '—'}</p></Td>
                    <Td><StatusBadge value={job.status} />{job.errorMessage && <p className="mt-1 max-w-[240px] text-xs text-red-700 line-clamp-2">{job.errorMessage}</p>}</Td>
                    <Td className="w-40"><JobProgress job={job} /></Td>
                    <Td className="whitespace-nowrap text-xs text-slate-500">{formatRelative(job.startedAt ?? job.createdAt)}</Td>
                    <Td>
                      {canWrite && job.canRetry && (
                        <Button size="sm" variant="secondary" icon={<RotateCcw className="h-3.5 w-3.5" />} loading={retry.isPending && retry.variables === job.id}
                          onClick={(event) => { event.stopPropagation(); retry.mutate(job.id); }}>Retry</Button>
                      )}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={jobs.data.page} totalPages={jobs.data.totalPages} totalElements={jobs.data.totalElements} size={size}
              onPage={setPage} onSize={(value) => { setSize(value); setPage(0); }} />
          </>
        ) : null}
      </Card>
      <JobDrawer jobId={selected} onClose={() => setSelected(null)} />
    </>
  );
}

function JobProgress({ job }: { job: ProcessingJob }) {
  return (
    <div>
      <div className="h-1.5 rounded-full bg-slate-100">
        <div className={`h-1.5 rounded-full ${job.status === 'FAILED' ? 'bg-red-500' : job.status === 'SUCCEEDED' ? 'bg-emerald-500' : 'bg-brand-600'}`} style={{ width: `${job.progress}%` }} />
      </div>
      <p className="mt-1 truncate text-xs text-slate-500">{job.stage ?? '—'}</p>
    </div>
  );
}

function JobDrawer({ jobId, onClose }: { jobId: number | null; onClose: () => void }) {
  const job = useQuery({
    queryKey: ['job', jobId],
    queryFn: () => getJob(jobId!),
    enabled: jobId !== null,
    refetchInterval: (query) => (query.state.data && ['QUEUED', 'RUNNING'].includes(query.state.data.job.status) ? 2000 : false)
  });
  const data = job.data;
  return (
    <Drawer open={jobId !== null} onClose={onClose} title={`Processing job #${jobId}`} subtitle={data?.job.fileName}>
      {job.isLoading && <SkeletonRows rows={4} columns={2} />}
      {job.isError && <Alert tone="danger">{friendlyError(job.error)}</Alert>}
      {data && (
        <div className="space-y-5">
          <KeyValueList items={[
            { label: 'Status', value: <StatusBadge value={data.job.status} /> },
            { label: 'Attempts', value: data.job.attempts },
            { label: 'Case', value: data.job.caseNumber },
            { label: 'Device', value: data.job.deviceName ?? '—' },
            { label: 'Created', value: `${formatUtc(data.job.createdAt)} by ${data.job.createdBy}` },
            { label: 'Finished', value: formatUtc(data.job.finishedAt) }
          ]} />
          {data.job.errorMessage && <Alert tone="danger" title="Error">{data.job.errorMessage}</Alert>}
          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Processing log</h3>
            <ol className="space-y-1.5 rounded-md border border-slate-200 bg-slate-50 p-3">
              {data.logs.map((entry) => (
                <li key={entry.id} className="mono flex gap-2">
                  <span className="shrink-0 text-slate-400">{entry.loggedAt.slice(11, 19)}</span>
                  <span className={`shrink-0 font-semibold ${entry.level === 'ERROR' ? 'text-red-700' : entry.level === 'WARN' ? 'text-amber-700' : 'text-slate-500'}`}>{entry.level}</span>
                  <span className="break-words text-slate-700">{entry.message}</span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      )}
    </Drawer>
  );
}
