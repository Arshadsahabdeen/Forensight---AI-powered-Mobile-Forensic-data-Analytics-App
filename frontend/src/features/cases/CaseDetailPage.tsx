import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { deleteCase, getCase } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, CardBody, CardHeader, KeyValueList, Stat } from '../../components/ui/Card';
import { Button, LinkButton } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/Badge';
import { Alert, EmptyState, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { Tabs } from '../../components/ui/Tabs';
import { ConfirmDialog } from '../../components/ui/Dialog';
import { formatRelative, formatUtc, humanize } from '../../lib/format';
import { EvidenceBrowser } from '../evidence/EvidenceBrowser';
import { EvidenceFilesPanel } from '../files/EvidenceFilesPanel';
import { TimelinePanel } from '../timeline/TimelinePanel';
import { RelationshipExplorer } from '../relationships/RelationshipExplorer';
import { AssistantPanel } from '../assistant/AssistantPanel';
import { WorkspacePanel } from '../workspace/WorkspacePanel';
import { CaseReportsPanel } from '../reports/CaseReportsPanel';
import type { CaseDetail } from '../../types/api';

const TAB_IDS = ['overview', 'evidence', 'files', 'timeline', 'relationships', 'assistant', 'workspace', 'reports', 'activity'] as const;
type TabId = (typeof TAB_IDS)[number];

export default function CaseDetailPage() {
  const { id } = useParams();
  const caseId = Number(id);
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab') as TabId | null;
  const tab: TabId = tabParam && TAB_IDS.includes(tabParam) ? tabParam : 'overview';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { canWrite } = usePermissions();
  const [confirmDelete, setConfirmDelete] = useState(false);

  const detail = useQuery({ queryKey: ['case', caseId], queryFn: () => getCase(caseId), enabled: Number.isFinite(caseId) });
  const remove = useMutation({
    mutationFn: () => deleteCase(caseId),
    onSuccess: () => {
      toast.success('Case deleted.');
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
      navigate('/cases');
    },
    onError: (error) => {
      setConfirmDelete(false);
      toast.error(friendlyError(error));
    }
  });

  if (detail.isLoading) return <div className="space-y-4"><Skeleton className="h-16" /><Skeleton className="h-64" /></div>;
  if (detail.isError || !detail.data) {
    return (
      <>
        <PageHeader title="Case" back={{ to: '/cases', label: 'Cases' }} />
        <Card><ErrorState message={friendlyError(detail.error)} onRetry={() => void detail.refetch()} /></Card>
      </>
    );
  }

  const data = detail.data;
  const o = data.overview;
  const setTab = (next: string) => setParams(next === 'overview' ? {} : { tab: next }, { replace: true });

  return (
    <>
      <PageHeader
        back={{ to: '/cases', label: 'Cases' }}
        title={<span className="flex flex-wrap items-baseline gap-x-3">{o.title}</span>}
        description={`${o.caseNumber} · ${o.category} · ${o.caseType}`}
        meta={<><StatusBadge value={o.status} /><StatusBadge value={o.priority} /><span className="text-xs text-slate-500">Lead: {o.leadInvestigator}</span></>}
        actions={
          <>
            {data.canEdit && <LinkButton variant="secondary" to={`/cases/${caseId}/edit`} icon={<Pencil className="h-4 w-4" />}>Edit</LinkButton>}
            {data.canDelete && <Button variant="danger" icon={<Trash2 className="h-4 w-4" />} onClick={() => setConfirmDelete(true)}>Delete</Button>}
          </>
        }
      />
      {!data.canEdit && canWrite && <div className="mb-4"><Alert tone="info">You have read-only access to this case.</Alert></div>}

      <Tabs label="Case sections" active={tab} onChange={setTab} tabs={[
        { id: 'overview', label: 'Overview' },
        { id: 'evidence', label: 'Evidence', count: data.evidenceSummary.totalRecords },
        { id: 'files', label: 'Files & integrity', count: data.evidenceSummary.evidenceFiles },
        { id: 'timeline', label: 'Timeline' },
        { id: 'relationships', label: 'Relationships' },
        { id: 'assistant', label: 'Assistant' },
        { id: 'workspace', label: 'Workspace', count: data.findingSummary.total },
        { id: 'reports', label: 'Reports', count: data.reports.length },
        { id: 'activity', label: 'Activity' }
      ]} />

      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} className="mt-6">
        {tab === 'overview' && <Overview data={data} onTab={setTab} />}
        {tab === 'evidence' && <EvidenceBrowser caseId={caseId} />}
        {tab === 'files' && <EvidenceFilesPanel caseId={caseId} canEdit={data.canEdit} devices={data.devices} />}
        {tab === 'timeline' && <TimelinePanel caseId={caseId} devices={data.devices} />}
        {tab === 'relationships' && <RelationshipExplorer caseId={caseId} devices={data.devices} />}
        {tab === 'assistant' && <AssistantPanel caseId={caseId} canCreateFindings={data.canEdit} devices={data.devices} />}
        {tab === 'workspace' && <WorkspacePanel caseId={caseId} canEdit={data.canEdit} />}
        {tab === 'reports' && <CaseReportsPanel caseId={caseId} canEdit={data.canEdit} />}
        {tab === 'activity' && <ActivityList data={data} />}
      </div>

      <ConfirmDialog open={confirmDelete} title="Delete case?" confirmLabel="Delete case" loading={remove.isPending}
        message={<>Delete <strong>{o.caseNumber}</strong> and its devices? The case has no evidence. This cannot be undone.</>}
        onConfirm={() => remove.mutate()} onCancel={() => setConfirmDelete(false)} />
    </>
  );
}

function Overview({ data, onTab }: { data: CaseDetail; onTab: (tab: string) => void }) {
  const o = data.overview;
  const s = data.evidenceSummary;
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Evidence records" value={s.totalRecords.toLocaleString()} hint={`${s.evidenceFiles} source file(s)`} />
        <Stat label="Files verified" value={`${s.filesVerified}/${s.evidenceFiles}`}
          tone={s.filesWithIntegrityIssues > 0 ? 'danger' : 'default'}
          hint={s.filesWithIntegrityIssues > 0 ? `${s.filesWithIntegrityIssues} with integrity issues` : 'Re-verify from the Files tab'} />
        <Stat label="Findings" value={data.findingSummary.total} hint={`${data.findingSummary.needsReview} awaiting review · ${data.findingSummary.aiGenerated} AI-assisted`}
          tone={data.findingSummary.needsReview > 0 ? 'warning' : 'default'} />
        <Stat label="Devices" value={data.devices.length} hint={s.filesFailedProcessing > 0 ? `${s.filesFailedProcessing} file(s) failed processing` : undefined}
          tone={s.filesFailedProcessing > 0 ? 'warning' : 'default'} />
      </div>
      {s.filesWithIntegrityIssues > 0 && (
        <Alert tone="danger" title="Integrity verification failed" action={<Button size="sm" variant="secondary" onClick={() => onTab('files')}>Review files</Button>}>
          At least one stored evidence file no longer matches the hash recorded at import, or is missing.
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Case details" />
          <CardBody>
            <p className="mb-4 whitespace-pre-wrap text-sm text-slate-700">{o.description}</p>
            <KeyValueList items={[
              { label: 'Case number', value: o.caseNumber },
              { label: 'Lead investigator', value: o.leadInvestigator },
              { label: 'Category', value: o.category },
              { label: 'Case type', value: o.caseType },
              { label: 'Location', value: o.location || '—' },
              { label: 'Opened', value: formatUtc(o.createdAt) },
              { label: 'Last updated', value: formatUtc(o.updatedAt) }
            ]} />
            {o.notes && <div className="mt-4 rounded-md bg-slate-50 p-3 text-sm text-slate-700"><p className="mb-1 text-xs font-medium text-slate-500">Notes</p>{o.notes}</div>}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Records by type" />
          <CardBody>
            {Object.keys(s.recordsByType).length === 0 ? (
              <p className="text-sm text-slate-500">No evidence imported yet.</p>
            ) : (
              <ul className="space-y-1.5 text-sm">
                {Object.entries(s.recordsByType).map(([type, count]) => (
                  <li key={type} className="flex justify-between"><span className="text-slate-600">{type}</span><span className="tabular-nums font-medium">{count}</span></li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>

      <Card>
        <CardHeader title="Devices" description="Handsets and media examined in this case"
          actions={data.canEdit && <LinkButton size="sm" variant="secondary" to={`/devices/new?caseId=${o.id}`} icon={<Plus className="h-4 w-4" />}>Add device</LinkButton>} />
        {data.devices.length === 0 ? (
          <EmptyState title="No devices registered" description="Add a device manually or import an extraction file; devices are registered from file metadata." />
        ) : (
          <ul className="divide-y divide-slate-100">
            {data.devices.map((device) => (
              <li key={device.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <Link to={`/devices/${device.id}`} className="link text-sm">{device.deviceName}</Link>
                  <p className="text-xs text-slate-500">{device.operatingSystem ?? 'Unknown OS'}{device.imei ? ` · IMEI ${device.imei}` : ''}</p>
                </div>
                <span className="text-xs text-slate-500">{device.evidenceCount} record(s)</span>
                <StatusBadge value={device.acquisitionStatus} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function ActivityList({ data }: { data: CaseDetail }) {
  return (
    <Card>
      <CardHeader title="Case activity" description="Audit trail entries for this case (most recent first)." />
      {data.activity.length === 0 ? (
        <EmptyState title="No activity recorded" />
      ) : (
        <ul className="divide-y divide-slate-100">
          {data.activity.map((item) => (
            <li key={item.id} className="flex flex-wrap items-start gap-3 px-5 py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm text-slate-800">{item.description ?? humanize(item.action)}</p>
                <p className="text-xs text-slate-500"><code className="mono">{item.action}</code> · {item.actor ?? 'system'}</p>
              </div>
              {!item.success && <StatusBadge value="FAILED" />}
              <span className="text-xs text-slate-500" title={formatUtc(item.timestamp)}>{formatRelative(item.timestamp)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
