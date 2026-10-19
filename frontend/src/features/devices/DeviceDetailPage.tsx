import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Trash2 } from 'lucide-react';
import { deleteDevice, getDevice } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, CardBody, CardHeader, KeyValueList, Stat } from '../../components/ui/Card';
import { Button, LinkButton } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/Badge';
import { ErrorState, Skeleton } from '../../components/ui/Feedback';
import { Tabs } from '../../components/ui/Tabs';
import { ConfirmDialog } from '../../components/ui/Dialog';
import { formatDate, formatUtc, humanize } from '../../lib/format';
import { EvidenceBrowser } from '../evidence/EvidenceBrowser';
import { TimelinePanel } from '../timeline/TimelinePanel';

export default function DeviceDetailPage() {
  const { id } = useParams();
  const deviceId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [tab, setTab] = useState('details');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const detail = useQuery({ queryKey: ['device', deviceId], queryFn: () => getDevice(deviceId) });
  const remove = useMutation({
    mutationFn: () => deleteDevice(deviceId),
    onSuccess: () => { toast.success('Device deleted.'); void queryClient.invalidateQueries({ queryKey: ['devices'] }); navigate('/devices'); },
    onError: (error) => { setConfirmDelete(false); toast.error(friendlyError(error)); }
  });

  if (detail.isLoading) return <Skeleton className="h-96" />;
  if (detail.isError || !detail.data) return <><PageHeader title="Device" back={{ to: '/devices', label: 'Devices' }} /><Card><ErrorState message={friendlyError(detail.error)} /></Card></>;
  const { device: d, evidenceCount, timelineCount, evidenceFileCount, reportCount, canEdit, canDelete } = detail.data;

  return (
    <>
      <PageHeader back={{ to: '/devices', label: 'Devices' }} title={d.deviceName ?? `Device #${d.id}`}
        description={[d.manufacturer, d.model, d.operatingSystem && `${d.operatingSystem} ${d.osVersion ?? ''}`.trim()].filter(Boolean).join(' · ')}
        meta={<><StatusBadge value={d.acquisitionStatus} />{d.caseId && <Link to={`/cases/${d.caseId}`} className="link text-sm">{d.caseNumber}</Link>}</>}
        actions={<>
          {canEdit && <LinkButton variant="secondary" to={`/devices/${d.id}/edit`} icon={<Pencil className="h-4 w-4" />}>Edit</LinkButton>}
          {canDelete && <Button variant="danger" icon={<Trash2 className="h-4 w-4" />} onClick={() => setConfirmDelete(true)}>Delete</Button>}
        </>} />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Evidence records" value={evidenceCount} />
        <Stat label="Dated records" value={timelineCount} hint={`${evidenceCount - timelineCount} without usable timestamp`} />
        <Stat label="Source files" value={evidenceFileCount} />
        <Stat label="Case reports" value={reportCount} />
      </div>

      <Tabs label="Device sections" active={tab} onChange={setTab} tabs={[
        { id: 'details', label: 'Details' },
        { id: 'evidence', label: 'Evidence', count: evidenceCount },
        { id: 'timeline', label: 'Timeline' }
      ]} />
      <div className="mt-6">
        {tab === 'details' && (
          <Card>
            <CardHeader title="Device information" />
            <CardBody>
              <KeyValueList columns={3} items={[
                { label: 'IMEI', value: d.imei ? <code className="mono">{d.imei}</code> : '—' },
                { label: 'Serial number', value: d.serialNumber ?? '—' },
                { label: 'Device type', value: d.deviceType ?? '—' },
                { label: 'Manufacturer / brand', value: [d.manufacturer, d.brand].filter(Boolean).join(' / ') || '—' },
                { label: 'Model', value: d.model ?? '—' },
                { label: 'Operating system', value: [d.operatingSystem, d.osVersion].filter(Boolean).join(' ') || '—' },
                { label: 'Acquisition method', value: humanize(d.acquisitionMethod) },
                { label: 'Seized', value: formatDate(d.seizedDate) },
                { label: 'Received', value: formatDate(d.receivedDate) },
                { label: 'Examiner', value: d.investigator ?? '—' },
                { label: 'Owner', value: [d.ownerName, d.ownerContact].filter(Boolean).join(' · ') || '—' },
                { label: 'Registered', value: `${formatUtc(d.createdAt)}${d.createdBy ? ` by ${d.createdBy}` : ''}` }
              ]} />
              {d.notes && <p className="mt-4 whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm text-slate-700">{d.notes}</p>}
            </CardBody>
          </Card>
        )}
        {tab === 'evidence' && <EvidenceBrowser deviceId={d.id} caseId={d.caseId ?? undefined} />}
        {tab === 'timeline' && d.caseId && (
          <TimelinePanel caseId={d.caseId} initialDeviceId={d.id} devices={[{ id: d.id, deviceName: d.deviceName ?? `Device #${d.id}` }]} />
        )}
      </div>
      <ConfirmDialog open={confirmDelete} title="Delete device?" confirmLabel="Delete device" loading={remove.isPending}
        message="This device has no imported evidence. Delete it? This cannot be undone." onConfirm={() => remove.mutate()} onCancel={() => setConfirmDelete(false)} />
    </>
  );
}
