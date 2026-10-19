import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Activity, AlertTriangle, ClipboardList, FolderPlus } from 'lucide-react';
import { getDashboard } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useAuth, usePermissions } from '../../contexts/AuthContext';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, CardBody, CardHeader, Stat } from '../../components/ui/Card';
import { LinkButton } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/Badge';
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { formatRelative, humanize } from '../../lib/format';
import type { Metric } from '../../types/api';

function BarList({ items, total }: { items: Metric[]; total: number }) {
  if (total === 0) return <p className="text-sm text-slate-500">No data yet.</p>;
  return (
    <ul className="space-y-2">
      {items.map((item) => (
        <li key={item.label}>
          <div className="mb-1 flex justify-between text-xs">
            <span className="text-slate-600">{humanize(item.label)}</span>
            <span className="font-medium tabular-nums text-slate-900">{item.value}</span>
          </div>
          <div className="h-1.5 rounded-full bg-slate-100">
            <div className="h-1.5 rounded-full bg-brand-600" style={{ width: `${(item.value / total) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const { canWrite } = usePermissions();
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: getDashboard, refetchInterval: 30_000 });
  const data = dashboard.data;

  return (
    <>
      <PageHeader
        title={`Welcome, ${user?.fullName?.split(' ')[0] ?? ''}`}
        description={data?.scope === 'ASSIGNED_CASES' ? 'Figures cover the cases assigned to you.' : 'Figures cover all cases on the platform.'}
        actions={canWrite && <LinkButton to="/cases/new" icon={<FolderPlus className="h-4 w-4" />}>New case</LinkButton>}
      />

      {dashboard.isError && <Card><ErrorState message={friendlyError(dashboard.error)} onRetry={() => void dashboard.refetch()} /></Card>}

      {dashboard.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} className="h-24" />)}
        </div>
      )}

      {data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Stat label="Open & active cases" value={data.cases.active} hint={`${data.cases.total} total · ${data.cases.openHighPriority} high or critical priority open`} />
            <Stat label="Evidence records" value={data.evidence.records.toLocaleString()} hint={`${data.evidence.files} source file(s) · ${data.evidence.devices} device(s)`} />
            <Stat
              label="Integrity issues"
              value={data.evidence.filesByIntegrity.MISMATCH + data.evidence.filesByIntegrity.MISSING}
              tone={data.evidence.filesByIntegrity.MISMATCH + data.evidence.filesByIntegrity.MISSING > 0 ? 'danger' : 'default'}
              hint={`${data.evidence.filesByIntegrity.VERIFIED} verified · ${data.evidence.filesByIntegrity.NOT_VERIFIED} not yet re-verified`}
            />
            <Stat
              label="Awaiting review"
              value={data.review.findingsNeedingReview + data.review.reportsAwaitingReview}
              tone={data.review.findingsNeedingReview + data.review.reportsAwaitingReview > 0 ? 'warning' : 'default'}
              hint={`${data.review.findingsNeedingReview} finding(s) · ${data.review.reportsAwaitingReview} report(s)`}
            />
          </div>

          {(data.processing.failed > 0 || data.processing.running > 0 || data.processing.queued > 0) && (
            <div className={`flex flex-wrap items-center gap-3 rounded-md border px-4 py-3 text-sm ${data.processing.failed > 0 ? 'border-red-200 bg-red-50 text-red-900' : 'border-sky-200 bg-sky-50 text-sky-900'}`}>
              {data.processing.failed > 0 ? <AlertTriangle className="h-4 w-4" /> : <Activity className="h-4 w-4" />}
              <span>
                Evidence processing: {data.processing.running} running, {data.processing.queued} queued, {data.processing.failed} failed.
              </span>
              <Link to="/processing" className="link ml-auto">Open processing monitor</Link>
            </div>
          )}

          <div className="grid gap-6 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader title="Recently updated cases" actions={<Link to="/cases" className="link text-sm">All cases</Link>} />
              {data.recentCases.length === 0 ? (
                <EmptyState icon={<ClipboardList className="h-6 w-6" />} title="No cases yet" description={canWrite ? 'Create a case to start an investigation.' : 'No cases are assigned to you.'} />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {data.recentCases.map((item) => (
                    <li key={item.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
                      <div className="min-w-0 flex-1">
                        <Link to={`/cases/${item.id}`} className="link text-sm">{item.caseNumber}</Link>
                        <p className="truncate text-sm text-slate-700">{item.title}</p>
                      </div>
                      <StatusBadge value={item.status} />
                      <StatusBadge value={item.priority} />
                      <span className="w-24 text-right text-xs text-slate-500">{formatRelative(item.updatedAt)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card>
              <CardHeader title="Cases by status" />
              <CardBody>
                <BarList items={data.cases.byStatus} total={data.cases.total} />
              </CardBody>
            </Card>
          </div>

          <div className="grid gap-6 xl:grid-cols-3">
            <Card>
              <CardHeader title="Processing jobs" actions={<Link to="/processing" className="link text-sm">Monitor</Link>} />
              {data.processing.recentJobs.length === 0 ? (
                <EmptyState title="No imports yet" description="Upload an extraction file from a case's Files tab." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {data.processing.recentJobs.map((job) => (
                    <li key={job.id} className="px-5 py-2.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate text-sm text-slate-800">{job.fileName ?? `Job #${job.id}`}</span>
                        <StatusBadge value={job.status} />
                      </div>
                      <p className="text-xs text-slate-500">{job.caseNumber} · {formatRelative(job.createdAt)}</p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card>
              <CardHeader title="Evidence by type" />
              <CardBody>
                <BarList items={data.evidence.recordsByType} total={data.evidence.records} />
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Recent activity" description="From the audit trail" />
              {data.recentActivities.length === 0 ? (
                <EmptyState title="No activity recorded" />
              ) : (
                <ul className="max-h-80 divide-y divide-slate-100 overflow-y-auto">
                  {data.recentActivities.map((item) => (
                    <li key={item.id} className="px-5 py-2.5">
                      <p className="text-sm text-slate-800">{item.description ?? humanize(item.action)}</p>
                      <p className="text-xs text-slate-500">
                        {item.actor ?? 'system'} · {formatRelative(item.timestamp)}
                        {!item.success && <span className="ml-1 text-red-600">· failed</span>}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
