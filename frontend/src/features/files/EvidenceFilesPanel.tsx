import { useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, FileUp, History, RotateCcw, ShieldCheck, UploadCloud } from 'lucide-react';
import { downloadFile } from '../../services/api';
import { getCustody, getEvidenceFiles, retryJob, uploadEvidenceFile, verifyEvidenceFile } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useToast } from '../../components/ui/Toast';
import { Card, CardBody, CardHeader, KeyValueList } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Field, Select } from '../../components/ui/Input';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { StatusBadge } from '../../components/ui/Badge';
import { Drawer } from '../../components/ui/Dialog';
import { HashValue } from '../../components/ui/PageHeader';
import { formatBytes, formatUtc, humanize } from '../../lib/format';
import type { CaseDetail, EvidenceFile } from '../../types/api';

export const ACCEPTED_EXTENSIONS = ['.xml', '.ufdr', '.zip'];
export const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;

/** Client-side pre-check; the server re-validates extension, content signature and size. */
export function validateUpload(file: File | null): string | null {
  if (!file) return 'Choose a file to upload.';
  const lower = file.name.toLowerCase();
  if (!ACCEPTED_EXTENSIONS.some((ext) => lower.endsWith(ext))) return 'Only .xml, .ufdr and .zip extraction files are supported.';
  if (file.size === 0) return 'The selected file is empty.';
  if (file.size > MAX_UPLOAD_BYTES) return `The file is ${formatBytes(file.size)}; the maximum is ${formatBytes(MAX_UPLOAD_BYTES)}.`;
  return null;
}

export function EvidenceFilesPanel({ caseId, canEdit, devices }: { caseId: number; canEdit: boolean; devices: CaseDetail['devices'] }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [deviceId, setDeviceId] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [progress, setProgress] = useState(0);
  const [custodyFor, setCustodyFor] = useState<EvidenceFile | null>(null);

  const files = useQuery({
    queryKey: ['evidence-files', caseId],
    queryFn: () => getEvidenceFiles(caseId),
    refetchInterval: (query) => (query.state.data?.some((f) => f.processingStatus === 'RECEIVED' || f.processingStatus === 'PROCESSING') ? 2000 : false)
  });

  const refreshCase = () => {
    void queryClient.invalidateQueries({ queryKey: ['evidence-files', caseId] });
    void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
    void queryClient.invalidateQueries({ queryKey: ['evidence-search'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const upload = useMutation({
    mutationFn: () => uploadEvidenceFile(caseId, file!, deviceId ? Number(deviceId) : undefined, setProgress),
    onSuccess: (result) => {
      const job = result.job;
      if (job.status === 'FAILED') toast.error(`Stored, but processing failed: ${job.errorMessage ?? 'see job log'}`);
      else toast.success(job.status === 'SUCCEEDED'
        ? `Imported ${result.file.recordsExtracted} record(s) from ${result.file.originalFileName}.`
        : `${result.file.originalFileName} stored and queued for processing.`);
      setFile(null);
      setProgress(0);
      if (inputRef.current) inputRef.current.value = '';
      refreshCase();
    },
    onError: (error) => {
      setProgress(0);
      setUploadError(friendlyError(error));
    }
  });

  const verify = useMutation({
    mutationFn: (id: number) => verifyEvidenceFile(id),
    onSuccess: (result) => {
      if (result.status === 'VERIFIED') toast.success('SHA-256 matches the value recorded at import.');
      else toast.error(`Integrity check: ${humanize(result.status)}. ${result.message}`);
      refreshCase();
    },
    onError: (error) => toast.error(friendlyError(error))
  });

  const retry = useMutation({
    mutationFn: (jobId: number) => retryJob(jobId),
    onSuccess: (job) => {
      if (job.status === 'FAILED') toast.error(`Retry failed: ${job.errorMessage ?? 'see job log'}`);
      else toast.success('Processing restarted from the stored original file.');
      refreshCase();
    },
    onError: (error) => toast.error(friendlyError(error))
  });

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null;
    setFile(selected);
    setUploadError(selected ? validateUpload(selected) ?? '' : '');
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const problem = validateUpload(file);
    setUploadError(problem ?? '');
    if (!problem) upload.mutate();
  }

  return (
    <div className="space-y-6">
      {canEdit && (
        <Card>
          <CardHeader title="Import an extraction file" description="Supported: platform XML schema (.xml) or a .ufdr/.zip archive containing it. Up to 100 MB." />
          <CardBody>
            <form onSubmit={submit} className="grid items-end gap-4 md:grid-cols-[1fr_240px_auto]">
              <Field label="File" error={uploadError || undefined} hint={file ? `${file.name} · ${formatBytes(file.size)}` : 'The original is stored read-only and hashed with SHA-256 on arrival.'}>
                {(props) => (
                  <input {...props} ref={inputRef} type="file" accept={ACCEPTED_EXTENSIONS.join(',')} onChange={onFile}
                    className="block w-full text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200" />
                )}
              </Field>
              <Field label="Device" hint="Leave automatic to use the device described in the file.">
                {(props) => (
                  <Select {...props} value={deviceId} onChange={(event) => setDeviceId(event.target.value)}>
                    <option value="">Automatic (from file metadata)</option>
                    {devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                  </Select>
                )}
              </Field>
              <Button type="submit" loading={upload.isPending} disabled={!file || Boolean(uploadError)} icon={<UploadCloud className="h-4 w-4" />}>
                {upload.isPending ? (progress < 100 ? `Uploading ${progress}%` : 'Processing…') : 'Upload'}
              </Button>
            </form>
          </CardBody>
        </Card>
      )}

      <Card>
        <CardHeader title="Evidence files" description="Original files received for this case, with integrity and processing status." />
        {files.isLoading ? (
          <SkeletonRows rows={3} columns={5} />
        ) : files.isError ? (
          <ErrorState message={friendlyError(files.error)} onRetry={() => void files.refetch()} />
        ) : files.data && files.data.length === 0 ? (
          <EmptyState icon={<FileUp className="h-6 w-6" />} title="No evidence files imported"
            description="Sample synthetic files are available in datasets/samples of the repository." />
        ) : (
          <ul className="divide-y divide-slate-100">
            {files.data?.map((item) => {
              const job = item.latestJob;
              const busy = item.processingStatus === 'RECEIVED' || item.processingStatus === 'PROCESSING';
              return (
                <li key={item.id} className="px-5 py-4">
                  <div className="flex flex-wrap items-start gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">{item.originalFileName}</p>
                      <p className="text-xs text-slate-500">
                        File #{item.id} · {item.sourceFormat} · {formatBytes(item.sizeBytes)} · imported {formatUtc(item.importedAt)} by {item.importedBy}
                        {item.deviceName && <> · {item.deviceName}</>}
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge value={item.processingStatus} title={job?.stage ?? undefined} />
                      <StatusBadge value={item.integrityStatus}
                        label={item.integrityStatus === 'NOT_VERIFIED' ? 'Not re-verified' : humanize(item.integrityStatus)}
                        title={item.lastVerifiedAt ? `Last verified ${formatUtc(item.lastVerifiedAt)} by ${item.lastVerifiedBy}` : undefined} />
                    </div>
                  </div>
                  <div className="mt-2"><HashValue value={item.sha256} /></div>
                  {busy && job && (
                    <div className="mt-3">
                      <div className="mb-1 flex justify-between text-xs text-slate-500"><span>{job.stage ?? 'Queued'}</span><span>{job.progress}%</span></div>
                      <div className="h-1.5 rounded-full bg-slate-100"><div className="h-1.5 rounded-full bg-brand-600 transition-all" style={{ width: `${job.progress}%` }} /></div>
                    </div>
                  )}
                  {item.processingStatus === 'PROCESSED' && (
                    <p className="mt-2 text-xs text-slate-600">
                      {item.recordsExtracted} record(s) imported · {item.duplicatesSkipped} duplicate(s) skipped · {item.timestampIssues} timestamp issue(s)
                    </p>
                  )}
                  {item.processingStatus === 'FAILED' && job?.errorMessage && (
                    <div className="mt-2"><Alert tone="danger">{job.errorMessage}</Alert></div>
                  )}
                  {(item.integrityStatus === 'MISMATCH' || item.integrityStatus === 'MISSING') && (
                    <div className="mt-2"><Alert tone="danger" title="Integrity check failed">The stored file does not match the SHA-256 recorded at import. Do not rely on records from this file until resolved.</Alert></div>
                  )}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button size="sm" variant="secondary" icon={<ShieldCheck className="h-4 w-4" />} loading={verify.isPending && verify.variables === item.id}
                      onClick={() => verify.mutate(item.id)}>Verify hash</Button>
                    <Button size="sm" variant="secondary" icon={<History className="h-4 w-4" />} onClick={() => setCustodyFor(item)}>Custody log</Button>
                    <Button size="sm" variant="ghost" icon={<Download className="h-4 w-4" />}
                      onClick={() => downloadFile(`/evidence-files/${item.id}/download`, item.originalFileName).catch((error) => toast.error(friendlyError(error)))}>
                      Download original
                    </Button>
                    {canEdit && job?.canRetry && (
                      <Button size="sm" variant="ghost" icon={<RotateCcw className="h-4 w-4" />} loading={retry.isPending && retry.variables === job.id}
                        onClick={() => retry.mutate(job.id)}>Retry processing</Button>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Card>
      <CustodyDrawer file={custodyFor} onClose={() => setCustodyFor(null)} />
    </div>
  );
}

function CustodyDrawer({ file, onClose }: { file: EvidenceFile | null; onClose: () => void }) {
  const custody = useQuery({ queryKey: ['custody', file?.id], queryFn: () => getCustody(file!.id), enabled: file !== null });
  return (
    <Drawer open={file !== null} onClose={onClose} title="Chain of custody" subtitle={file?.originalFileName}>
      {file && (
        <div className="space-y-5">
          <KeyValueList columns={1} items={[
            { label: 'SHA-256 recorded at import', value: <HashValue value={file.sha256} /> },
            { label: 'Imported', value: `${formatUtc(file.importedAt)} by ${file.importedBy}` },
            { label: 'Last verification', value: file.lastVerifiedAt ? `${humanize(file.integrityStatus)} — ${formatUtc(file.lastVerifiedAt)} by ${file.lastVerifiedBy}` : 'Not re-verified since import' }
          ]} />
          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Recorded events</h3>
            {custody.isLoading && <SkeletonRows rows={4} columns={2} />}
            {custody.isError && <Alert tone="danger">{friendlyError(custody.error)}</Alert>}
            {custody.data && custody.data.length === 0 && <p className="text-sm text-slate-500">No events recorded.</p>}
            <ol className="relative space-y-4 border-l border-slate-200 pl-4">
              {custody.data?.map((event) => (
                <li key={event.id}>
                  <span className={`absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full border-2 border-white ${event.success ? 'bg-emerald-500' : 'bg-red-500'}`} />
                  <p className="text-sm font-medium text-slate-900">{humanize(event.action)}</p>
                  <p className="text-xs text-slate-500">{formatUtc(event.timestamp)} · {event.actor ?? 'system'}</p>
                  {event.description && <p className="mt-1 break-words text-xs text-slate-600">{event.description}</p>}
                </li>
              ))}
            </ol>
          </div>
          <p className="text-xs text-slate-500">Events come from the append-only, hash-chained audit log. Administrators can verify the chain from the Audit log page.</p>
        </div>
      )}
    </Drawer>
  );
}
