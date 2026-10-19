import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bookmark, BookmarkCheck, ExternalLink, FilePlus2, Link2 } from 'lucide-react';
import { addBookmark, getEvidenceDetail, getRelatedEvidence, removeBookmark } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { Drawer } from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { KeyValueList } from '../../components/ui/Card';
import { Alert, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { HashValue } from '../../components/ui/PageHeader';
import { BASIS_LABELS, formatUtc, humanize, TIMESTAMP_LABELS, describeParticipants } from '../../lib/format';
import { FindingDialog } from '../workspace/FindingDialog';

/** Full evidence record with provenance, normalized view and explainable related records. */
export function EvidenceDrawer({ evidenceId, onClose, onSelect }: { evidenceId: number | null; onClose: () => void; onSelect?: (id: number) => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { canWrite } = usePermissions();
  const [findingOpen, setFindingOpen] = useState(false);
  const open = evidenceId !== null;

  const detail = useQuery({ queryKey: ['evidence', evidenceId], queryFn: () => getEvidenceDetail(evidenceId!), enabled: open });
  const related = useQuery({ queryKey: ['evidence', evidenceId, 'related'], queryFn: () => getRelatedEvidence(evidenceId!), enabled: open });

  const evidence = detail.data?.evidence;
  const bookmark = useMutation({
    mutationFn: async () => {
      if (!evidence?.caseId) return;
      if (detail.data?.bookmarkId) await removeBookmark(evidence.caseId, detail.data.bookmarkId);
      else await addBookmark(evidence.caseId, evidence.id);
    },
    onSuccess: () => {
      toast.success(detail.data?.bookmarkId ? 'Bookmark removed.' : 'Evidence bookmarked.');
      void queryClient.invalidateQueries({ queryKey: ['evidence', evidenceId] });
      void queryClient.invalidateQueries({ queryKey: ['workspace'] });
    },
    onError: (error) => toast.error(friendlyError(error))
  });

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={evidence ? `${humanize(evidence.artifactType)} record #${evidence.id}` : 'Evidence record'}
      subtitle={evidence && (
        <span>
          <Link className="link" to={`/cases/${evidence.caseId}`}>{evidence.caseNumber}</Link>
          {evidence.deviceName && <> · <Link className="link" to={`/devices/${evidence.deviceId}`}>{evidence.deviceName}</Link></>}
        </span>
      )}
      actions={evidence && canWrite && (
        <>
          <Button size="sm" variant="secondary" loading={bookmark.isPending} onClick={() => bookmark.mutate()}
            icon={detail.data?.bookmarkId ? <BookmarkCheck className="h-4 w-4 text-brand-700" /> : <Bookmark className="h-4 w-4" />}>
            {detail.data?.bookmarkId ? 'Bookmarked' : 'Bookmark'}
          </Button>
          <Button size="sm" variant="secondary" onClick={() => setFindingOpen(true)} icon={<FilePlus2 className="h-4 w-4" />}>
            Add finding
          </Button>
        </>
      )}
    >
      {detail.isLoading && <div className="space-y-3"><Skeleton className="h-5 w-1/2" /><Skeleton className="h-24 w-full" /><Skeleton className="h-40 w-full" /></div>}
      {detail.isError && <ErrorState message={friendlyError(detail.error)} onRetry={() => void detail.refetch()} />}
      {evidence && (
        <div className="space-y-6">
          <section>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Original record (as extracted)</h3>
            {(evidence.title || evidence.content) && (
              <div className="mb-3 rounded-md border border-slate-200 bg-slate-50 p-3">
                {evidence.title && <p className="text-sm font-medium text-slate-900">{evidence.title}</p>}
                {evidence.content && <p className="mt-1 whitespace-pre-wrap break-words text-sm text-slate-700">{evidence.content}</p>}
              </div>
            )}
            <KeyValueList items={[
              { label: 'Type / category', value: <span className="flex flex-wrap gap-1"><Badge>{evidence.artifactType ?? 'Unknown'}</Badge><Badge tone="info">{humanize(evidence.category)}</Badge></span> },
              { label: 'Participants', value: describeParticipants(evidence) || '—' },
              { label: 'Location', value: evidence.location ?? (evidence.latitude != null && evidence.longitude != null ? `${evidence.latitude}, ${evidence.longitude}` : '—') },
              {
                label: 'Coordinates',
                value: evidence.latitude != null && evidence.longitude != null ? (
                  <a className="link inline-flex items-center gap-1" target="_blank" rel="noreferrer noopener"
                    href={`https://www.openstreetmap.org/?mlat=${evidence.latitude}&mlon=${evidence.longitude}#map=16/${evidence.latitude}/${evidence.longitude}`}>
                    {evidence.latitude.toFixed(5)}, {evidence.longitude.toFixed(5)} <ExternalLink className="h-3 w-3" />
                  </a>
                ) : '—'
              }
            ]} />
          </section>

          <section>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Timestamp</h3>
            <KeyValueList items={[
              { label: 'Normalized (UTC)', value: evidence.eventTime ? formatUtc(evidence.eventTime) : 'Undated' },
              { label: 'Derivation', value: <StatusBadge value={evidence.timestampStatus} label={TIMESTAMP_LABELS[evidence.timestampStatus]} /> },
              { label: 'Original text in source', value: evidence.originalTimestamp ? <code className="mono">{evidence.originalTimestamp}</code> : '—' },
              { label: 'Declared timezone', value: evidence.originalTimezone ?? 'None declared' }
            ]} />
            {evidence.timestampStatus === 'ASSUMED_UTC' && (
              <p className="mt-2 text-xs text-amber-800">The source gave no timezone, so the time was interpreted as UTC. The actual local time may differ.</p>
            )}
          </section>

          <section>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Provenance</h3>
            <KeyValueList columns={1} items={[
              { label: 'Source file', value: evidence.fileName ? `${evidence.fileName}${evidence.sourceFileId ? ` (file #${evidence.sourceFileId})` : ''}` : '—' },
              { label: 'Location in source', value: evidence.sourceRecordRef ? <code className="mono">{evidence.sourceRecordRef}</code> : '—' },
              { label: 'Source file SHA-256', value: evidence.hashSha256 ? <HashValue value={evidence.hashSha256} /> : <span className="text-slate-500">{evidence.hashNote ?? 'Not available'}</span> },
              { label: 'Record hash (extracted fields)', value: <HashValue value={evidence.recordHash} label="record hash" /> },
              { label: 'Imported', value: formatUtc(evidence.createdAt) }
            ]} />
          </section>

          {detail.data?.normalized && (
            <section>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Normalized view (derived)</h3>
              <p className="mb-2 text-xs text-slate-500">Derived by the platform ({detail.data.normalized.sourceTool}) from the original record above. The original is unchanged.</p>
              <pre className="mono max-h-56 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-slate-700">
                {JSON.stringify(detail.data.normalized.metadata, null, 2)}
              </pre>
            </section>
          )}

          {detail.data && detail.data.findings.length > 0 && (
            <section>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Cited by findings</h3>
              <ul className="space-y-1 text-sm">
                {detail.data.findings.map((finding) => (
                  <li key={finding.id} className="flex flex-wrap items-center gap-2">
                    <span className="text-slate-900">{finding.title}</span>
                    <StatusBadge value={finding.basis} label={BASIS_LABELS[finding.basis]} />
                    <StatusBadge value={finding.reviewStatus} />
                  </li>
                ))}
              </ul>
            </section>
          )}

          <section>
            <h3 className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <Link2 className="h-3.5 w-3.5" aria-hidden /> Related records
            </h3>
            {related.isLoading && <Skeleton className="h-16 w-full" />}
            {related.isError && <Alert tone="danger">{friendlyError(related.error)}</Alert>}
            {related.data && related.data.length === 0 && <p className="text-sm text-slate-500">No other records in this case share an identifier, name, location or content with this record.</p>}
            {related.data && related.data.length > 0 && (
              <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
                {related.data.map((item) => (
                  <li key={item.id} className="p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <button type="button" className="link text-sm" onClick={() => onSelect?.(item.id)}>
                        #{item.id} {humanize(item.evidence.artifactType)}
                      </button>
                      <StatusBadge value={item.basis} label={BASIS_LABELS[item.basis]} />
                      <span className="text-xs text-slate-500">{item.evidence.eventTime ? formatUtc(item.evidence.eventTime) : 'Undated'} · {item.evidence.deviceName}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-600">{item.reason}</p>
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-2 text-xs text-slate-500">Related means the records share data. It does not establish identity, intent or wrongdoing.</p>
          </section>
        </div>
      )}
      {evidence?.caseId && (
        <FindingDialog open={findingOpen} onClose={() => setFindingOpen(false)} caseId={evidence.caseId} initialEvidenceIds={[evidence.id]} />
      )}
    </Drawer>
  );
}
