import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createDevice, getDevice, updateDevice } from '../../services/forensics';
import { fieldErrors, friendlyError } from '../../services/errors';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, CardBody } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Field, Input, Select, Textarea } from '../../components/ui/Input';
import { Alert, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { CasePicker } from '../../components/common/CasePicker';
import { humanize } from '../../lib/format';
import { ACQUISITION_METHODS, DEVICE_STATUSES, validateDevice } from './deviceOptions';
import type { DeviceRequest } from '../../types/api';

const EMPTY: DeviceRequest = {
  caseId: 0,
  manufacturer: '',
  brand: '',
  model: '',
  deviceName: '',
  imei: '',
  serialNumber: '',
  operatingSystem: '',
  osVersion: '',
  deviceType: 'Smartphone',
  ownerName: '',
  ownerContact: '',
  investigator: '',
  acquisitionMethod: 'LOGICAL_EXTRACTION',
  acquisitionStatus: 'SEIZED',
  seizedDate: '',
  receivedDate: '',
  notes: ''
};

export default function DeviceFormPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const deviceId = id ? Number(id) : undefined;
  const editing = deviceId !== undefined;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [values, setValues] = useState<DeviceRequest>({ ...EMPTY, caseId: Number(params.get('caseId')) || 0 });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const existing = useQuery({ queryKey: ['device', deviceId], queryFn: () => getDevice(deviceId!), enabled: editing });

  useEffect(() => {
    const d = existing.data?.device;
    if (!d) return;
    setValues({
      caseId: d.caseId ?? 0, manufacturer: d.manufacturer ?? '', brand: d.brand ?? '', model: d.model ?? '', deviceName: d.deviceName ?? '',
      imei: d.imei ?? '', serialNumber: d.serialNumber ?? '', operatingSystem: d.operatingSystem ?? '', osVersion: d.osVersion ?? '',
      deviceType: d.deviceType ?? '', ownerName: d.ownerName ?? '', ownerContact: d.ownerContact ?? '', investigator: d.investigator ?? '',
      acquisitionMethod: d.acquisitionMethod ?? 'OTHER', acquisitionStatus: d.acquisitionStatus ?? 'SEIZED',
      seizedDate: d.seizedDate ?? '', receivedDate: d.receivedDate ?? '', notes: d.notes ?? ''
    });
  }, [existing.data]);

  const save = useMutation({
    mutationFn: (body: DeviceRequest) => (editing ? updateDevice(deviceId!, body) : createDevice(body)),
    onSuccess: (saved) => {
      toast.success(editing ? 'Device updated.' : 'Device added.');
      void queryClient.invalidateQueries({ queryKey: ['devices'] });
      void queryClient.invalidateQueries({ queryKey: ['device', saved.id] });
      void queryClient.invalidateQueries({ queryKey: ['case'] });
      navigate(`/devices/${saved.id}`);
    },
    onError: (error) => setErrors({ form: friendlyError(error), ...fieldErrors(error) })
  });

  function set<K extends keyof DeviceRequest>(key: K, value: DeviceRequest[K]) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: '' }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const next = validateDevice(values);
    setErrors(next as Record<string, string>);
    if (Object.keys(next).length) return;
    save.mutate({ ...values, seizedDate: values.seizedDate || null, receivedDate: values.receivedDate || null });
  }

  if (editing && existing.isLoading) return <Skeleton className="h-96" />;
  if (editing && existing.isError) return <Card><ErrorState message={friendlyError(existing.error)} /></Card>;
  if (editing && existing.data && !existing.data.canEdit) return <Alert tone="warning" title="Read-only">You can view this device but not edit it.</Alert>;

  const text = (key: keyof DeviceRequest, label: string, options: { required?: boolean; hint?: string; max?: number } = {}) => (
    <Field label={label} required={options.required} error={errors[key]} hint={options.hint}>
      {(props) => <Input {...props} maxLength={options.max ?? 160} value={String(values[key] ?? '')} onChange={(event) => set(key, event.target.value as never)} />}
    </Field>
  );

  return (
    <>
      <PageHeader title={editing ? 'Edit device' : 'Add device'} back={{ to: editing ? `/devices/${deviceId}` : '/devices', label: editing ? 'Back to device' : 'Devices' }} />
      <Card className="max-w-4xl">
        <CardBody>
          <form onSubmit={submit} noValidate className="space-y-6">
            {errors.form && <Alert tone="danger">{errors.form}</Alert>}
            <Field label="Case" required error={errors.caseId}
              hint={editing && existing.data && existing.data.evidenceCount > 0 ? 'Devices with imported evidence cannot be moved to another case.' : undefined}>
              {(props) => <CasePicker id={props.id} value={values.caseId || undefined} onChange={(value) => set('caseId', value ?? 0)} />}
            </Field>
            <fieldset className="grid gap-4 sm:grid-cols-2">
              <legend className="mb-2 text-sm font-semibold text-slate-900">Identification</legend>
              {text('deviceName', 'Device name', { required: true, hint: 'How investigators refer to it, e.g. “Suspect handset A”.' })}
              {text('imei', 'IMEI', { hint: '14–16 digits. Leave empty if not applicable.', max: 16 })}
              {text('manufacturer', 'Manufacturer', { required: true, max: 120 })}
              {text('brand', 'Brand', { max: 120 })}
              {text('model', 'Model', { required: true, max: 120 })}
              {text('serialNumber', 'Serial number', { max: 120 })}
              {text('operatingSystem', 'Operating system', { required: true, max: 120 })}
              {text('osVersion', 'OS version', { max: 80 })}
              {text('deviceType', 'Device type', { max: 80 })}
            </fieldset>
            <fieldset className="grid gap-4 sm:grid-cols-2">
              <legend className="mb-2 text-sm font-semibold text-slate-900">Acquisition</legend>
              <Field label="Acquisition method" required>
                {(props) => (
                  <Select {...props} value={values.acquisitionMethod} onChange={(event) => set('acquisitionMethod', event.target.value as DeviceRequest['acquisitionMethod'])}>
                    {ACQUISITION_METHODS.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
                  </Select>
                )}
              </Field>
              <Field label="Status" required>
                {(props) => (
                  <Select {...props} value={values.acquisitionStatus} onChange={(event) => set('acquisitionStatus', event.target.value as DeviceRequest['acquisitionStatus'])}>
                    {DEVICE_STATUSES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
                  </Select>
                )}
              </Field>
              <Field label="Seized on" error={errors.seizedDate}>
                {(props) => <Input {...props} type="date" value={values.seizedDate ?? ''} onChange={(event) => set('seizedDate', event.target.value)} />}
              </Field>
              <Field label="Received on" error={errors.receivedDate}>
                {(props) => <Input {...props} type="date" value={values.receivedDate ?? ''} onChange={(event) => set('receivedDate', event.target.value)} />}
              </Field>
              {text('investigator', 'Examiner', { max: 120 })}
            </fieldset>
            <fieldset className="grid gap-4 sm:grid-cols-2">
              <legend className="mb-2 text-sm font-semibold text-slate-900">Owner (as recorded at seizure)</legend>
              {text('ownerName', 'Owner name')}
              {text('ownerContact', 'Owner contact')}
            </fieldset>
            <Field label="Notes" error={errors.notes}>
              {(props) => <Textarea {...props} rows={3} maxLength={4000} value={values.notes ?? ''} onChange={(event) => set('notes', event.target.value)} />}
            </Field>
            <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
              <Button variant="secondary" onClick={() => navigate(-1)} disabled={save.isPending}>Cancel</Button>
              <Button type="submit" loading={save.isPending}>{editing ? 'Save changes' : 'Add device'}</Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </>
  );
}
