import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { HardDrive, Pencil, Plus, Trash2 } from 'lucide-react';
import { deleteDevice, getDevices } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { usePermissions } from '../../contexts/AuthContext';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { IconButton, LinkButton } from '../../components/ui/Button';
import { SearchInput, Select } from '../../components/ui/Input';
import { StatusBadge } from '../../components/ui/Badge';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Pagination, SortableTh, Table, Td, Th, Tr } from '../../components/ui/Table';
import { ConfirmDialog } from '../../components/ui/Dialog';
import { CasePicker } from '../../components/common/CasePicker';
import { formatRelative, humanize } from '../../lib/format';
import { DEVICE_STATUSES } from './deviceOptions';
import type { DeviceListItem } from '../../types/api';

type SortField = 'deviceName' | 'manufacturer' | 'imei' | 'operatingSystem' | 'acquisitionStatus' | 'updatedAt';

export default function DeviceListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { canWrite, isAdmin } = usePermissions();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [caseId, setCaseId] = useState<number | undefined>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sort, setSort] = useState<SortField>('updatedAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [pendingDelete, setPendingDelete] = useState<DeviceListItem | null>(null);
  const debounced = useDebounce(search);

  const params = { page, size, sortBy: sort, sortDir: direction, search: debounced, status, caseId };
  const devices = useQuery({ queryKey: ['devices', params], queryFn: () => getDevices(params), placeholderData: keepPreviousData });
  const remove = useMutation({
    mutationFn: (id: number) => deleteDevice(id),
    onSuccess: () => { toast.success('Device deleted.'); setPendingDelete(null); void queryClient.invalidateQueries({ queryKey: ['devices'] }); },
    onError: (error) => { toast.error(friendlyError(error)); setPendingDelete(null); }
  });

  function toggleSort(field: SortField) {
    if (field === sort) setDirection((value) => (value === 'asc' ? 'desc' : 'asc'));
    else { setSort(field); setDirection('asc'); }
    setPage(0);
  }

  return (
    <>
      <PageHeader title="Devices" description="Handsets and media registered to cases you can access."
        actions={canWrite && <LinkButton to="/devices/new" icon={<Plus className="h-4 w-4" />}>Add device</LinkButton>} />
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-[1fr_260px_220px]">
          <SearchInput aria-label="Search devices" placeholder="Search name, make, model, IMEI, serial, case…" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} />
          <CasePicker allowAll value={caseId} onChange={(value) => { setCaseId(value); setPage(0); }} />
          <Select aria-label="Status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {DEVICE_STATUSES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
          </Select>
        </div>
        {devices.isLoading ? <SkeletonRows rows={8} columns={5} /> : devices.isError ? (
          <ErrorState message={friendlyError(devices.error)} onRetry={() => void devices.refetch()} />
        ) : devices.data && devices.data.items.length === 0 ? (
          <EmptyState icon={<HardDrive className="h-6 w-6" />} title="No devices found" description="Devices are added manually or registered automatically when an extraction file is imported." />
        ) : devices.data ? (
          <>
            <Table minWidth={860}>
              <thead>
                <tr>
                  <SortableTh label="Device" field="deviceName" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Make / model" field="manufacturer" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="IMEI" field="imei" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="OS" field="operatingSystem" sort={sort} direction={direction} onSort={toggleSort} />
                  <Th>Case</Th>
                  <SortableTh label="Status" field="acquisitionStatus" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Updated" field="updatedAt" sort={sort} direction={direction} onSort={toggleSort} />
                  <Th><span className="sr-only">Actions</span></Th>
                </tr>
              </thead>
              <tbody className={devices.isFetching ? 'opacity-60' : ''}>
                {devices.data.items.map((item) => (
                  <Tr key={item.id} onClick={() => navigate(`/devices/${item.id}`)}>
                    <Td><Link to={`/devices/${item.id}`} className="link" onClick={(event) => event.stopPropagation()}>{item.deviceName ?? `Device #${item.id}`}</Link></Td>
                    <Td>{[item.manufacturer, item.model].filter(Boolean).join(' ') || '—'}</Td>
                    <Td className="mono">{item.imei ?? '—'}</Td>
                    <Td>{item.operatingSystem ?? '—'}</Td>
                    <Td>{item.caseId ? <Link to={`/cases/${item.caseId}`} className="link" onClick={(event) => event.stopPropagation()}>{item.caseNumber}</Link> : '—'}</Td>
                    <Td><StatusBadge value={item.acquisitionStatus} /></Td>
                    <Td className="whitespace-nowrap text-xs text-slate-500">{formatRelative(item.updatedAt)}</Td>
                    <Td>
                      <div className="flex justify-end gap-1" onClick={(event) => event.stopPropagation()}>
                        {canWrite && <IconButton label="Edit device" onClick={() => navigate(`/devices/${item.id}/edit`)}><Pencil className="h-4 w-4" /></IconButton>}
                        {isAdmin && <IconButton label="Delete device" className="hover:text-red-600" onClick={() => setPendingDelete(item)}><Trash2 className="h-4 w-4" /></IconButton>}
                      </div>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={devices.data.page} totalPages={devices.data.totalPages} totalElements={devices.data.totalElements} size={size}
              onPage={setPage} onSize={(value) => { setSize(value); setPage(0); }} />
          </>
        ) : null}
      </Card>
      <ConfirmDialog open={pendingDelete !== null} title="Delete device?" confirmLabel="Delete device" loading={remove.isPending}
        message={<>Delete <strong>{pendingDelete?.deviceName}</strong>? Devices with imported evidence cannot be deleted; archive them instead.</>}
        onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)} onCancel={() => setPendingDelete(null)} />
    </>
  );
}
