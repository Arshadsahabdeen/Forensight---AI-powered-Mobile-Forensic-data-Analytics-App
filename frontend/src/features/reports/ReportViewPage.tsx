import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Download, ShieldAlert, ShieldCheck } from 'lucide-react';
import { downloadFile } from '../../services/api';
import { getReport, reviewReport } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useAuth, usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { PageHeader, HashValue } from '../../components/ui/PageHeader';
import { Card, CardBody, CardHeader, KeyValueList } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Alert, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { Field, Textarea } from '../../components/ui/Input';
import { Dialog } from '../../components/ui/Dialog';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { BASIS_LABELS, formatBytes, formatUtc, humanize } from '../../lib/format';
import type { EvidenceRef, ReportSnapshot } from '../../types/api';

export default function ReportViewPage() {
  const { id } = useParams();
  const reportId = Number(id);
  const queryClient = useQueryClient();
  const toast = useToast();
  const { user } = useAuth();
  const { canReview } = usePermissions();
  const [reviewOpen, setReviewOpen] = useState(false);
  const [comment, setComment] = useState('');
  const report = useQuery({ queryKey: ['report', reportId], queryFn: () => getReport(reportId) });
  const review = useMutation({
    mutationFn: () => reviewReport(reportId, comment.trim() || undefined),
    onSuccess: () => {
      toast.success('Report marked as reviewed.');
      setReviewOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['report', reportId] });
      void queryClient.invalidateQueries({ queryKey: ['reports'] });
    },
    onError: (error) => toast.error(friendlyError(error))
  });

  if (report.isLoading) return <div className="space-y-4"><Skeleton className="h-16" /><Skeleton className="h-96" /></div>;
  if (report.isError || !report.data) return <><PageHeader title="Report" back={{ to: '/reports', label: 'Reports' }} /><Card><ErrorState message={friendlyError(report.error)} /></Card></>;

  const { summary, snapshot, snapshotVerified, legacy } = report.data;
  const canReviewThis = canReview && snapshot && summary.status !== 'REVIEWED' && summary.generatedBy !== user?.username;

  return (
    <>
      <PageHeader
        back={{ to: summary.caseId ? `/cases/${summary.caseId}?tab=reports` : '/reports', label: summary.caseId ? 'Back to case reports' : 'Reports' }}
        title={summary.title ?? summary.reportNumber ?? `Report #${summary.id}`}
        description={`${summary.reportNumber} · case ${summary.caseNumber ?? '—'}`}
        meta={<>
          <StatusBadge value={summary.status} />
          {snapshot && (snapshotVerified
            ? <Badge tone="success"><ShieldCheck className="h-3 w-3" />Snapshot verified</Badge>
            : <Badge tone="danger"><ShieldAlert className="h-3 w-3" />Snapshot hash mismatch</Badge>)}
        </>}
        actions={<>
          {canReviewThis && <Button variant="secondary" icon={<CheckCircle2 className="h-4 w-4" />} onClick={() => setReviewOpen(true)}>Mark reviewed</Button>}
          {snapshot && (
            <Button icon={<Download className="h-4 w-4" />}
              onClick={() => downloadFile(`/reports/${summary.id}/pdf`, `${summary.reportNumber}.pdf`).catch((error) => toast.error(friendlyError(error)))}>
              Download PDF
            </Button>
          )}
        </>}
      />

      {legacy && (
        <div className="space-y-4">
          <Alert tone="warning" title="Legacy report">{legacy.notice}</Alert>
          <Card><CardBody><KeyValueList columns={1} items={[
            { label: 'Stored summary', value: legacy.aiSummary },
            { label: 'Timeline summary', value: legacy.timelineSummary },
            { label: 'Risk level (legacy field)', value: legacy.riskLevel }
          ]} /></CardBody></Card>
        </div>
      )}

      {snapshot && <SnapshotView snapshot={snapshot} sha={summary.contentSha256} verified={Boolean(snapshotVerified)} reviewedBy={summary.reviewedBy} reviewedAt={summary.reviewedAt} />}

      <Dialog open={reviewOpen} onClose={() => setReviewOpen(false)} title="Mark report as reviewed"
        footer={<><Button variant="secondary" onClick={() => setReviewOpen(false)}>Cancel</Button><Button loading={review.isPending} onClick={() => review.mutate()}>Mark reviewed</Button></>}>
        <div className="space-y-3">
          <p className="text-sm text-slate-600">Confirm you have checked this report against the cited records. The review is recorded in the audit log; the report content does not change.</p>
          {(summary.unreviewedAiFindings ?? 0) > 0 && <Alert tone="warning">This report contains {summary.unreviewedAiFindings} AI-assisted finding(s) that were not reviewed when it was generated.</Alert>}
          <Field label="Comment (optional)">{(props) => <Textarea {...props} rows={3} maxLength={1000} value={comment} onChange={(event) => setComment(event.target.value)} />}</Field>
        </div>
      </Dialog>
    </>
  );
}

function RefRow({ item }: { item: EvidenceRef }) {
  return (
    <Tr>
      <Td className="mono whitespace-nowrap">#{item.evidenceId}</Td>
      <Td className="text-xs">{item.eventTime ? formatUtc(item.eventTime) : 'Undated'}<p className="mono text-slate-400">{item.originalTimestamp ?? '—'} [{item.timestampStatus}]</p></Td>
      <Td className="text-xs"><p className="text-slate-800">{item.summary ?? '—'}</p><p className="text-slate-500">{item.participants}</p></Td>
      <Td className="text-xs text-slate-500">{item.sourceFileId ? `file #${item.sourceFileId}` : 'no source file'}<p className="mono">{item.sourceRecordRef ?? ''}</p></Td>
    </Tr>
  );
}

function SnapshotView({ snapshot: s, sha, verified, reviewedBy, reviewedAt }: { snapshot: ReportSnapshot; sha: string | null; verified: boolean; reviewedBy: string | null; reviewedAt: string | null }) {
  const c = s.caseDetails;
  const o = s.evidenceOverview;
  return (
    <div className="space-y-6">
      {!verified && <Alert tone="danger" title="Integrity warning">The stored snapshot does not match the SHA-256 recorded when the report was generated. Do not rely on this report.</Alert>}
      <Card>
        <CardBody>
          <KeyValueList items={[
            { label: 'Generated', value: `${formatUtc(s.generatedAt)} by ${s.generatedBy}` },
            { label: 'Prepared by', value: s.preparedBy },
            { label: 'Review', value: reviewedAt ? `Reviewed by ${reviewedBy} on ${formatUtc(reviewedAt)}` : 'Not reviewed' },
            { label: 'Schema', value: s.schemaVersion },
            { label: 'Snapshot SHA-256', value: <HashValue value={sha} /> }
          ]} />
          <p className="mt-4 rounded-md bg-slate-50 p-3 text-xs text-slate-600">{s.disclaimer}</p>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="1. Case details" />
        <CardBody>
          <p className="mb-4 whitespace-pre-wrap text-sm text-slate-700">{c.description}</p>
          <KeyValueList columns={3} items={[
            { label: 'Case number', value: c.caseNumber },
            { label: 'Status / priority', value: `${humanize(c.status)} / ${humanize(c.priority)}` },
            { label: 'Lead investigator', value: c.leadInvestigator },
            { label: 'Category', value: c.category },
            { label: 'Type', value: c.caseType },
            { label: 'Opened', value: formatUtc(c.createdAt) }
          ]} />
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="2. Devices" />
        <Table minWidth={700}>
          <thead><tr><Th>Device</Th><Th>IMEI / serial</Th><Th>OS</Th><Th>Acquisition</Th><Th className="text-right">Records</Th></tr></thead>
          <tbody>
            {s.devices.map((d) => (
              <Tr key={d.id}>
                <Td>{d.deviceName}<p className="text-xs text-slate-500">{[d.manufacturer, d.model].filter(Boolean).join(' ')}</p></Td>
                <Td className="mono">{d.imei ?? '—'}<br />{d.serialNumber ?? ''}</Td>
                <Td>{[d.operatingSystem, d.osVersion].filter(Boolean).join(' ') || '—'}</Td>
                <Td className="text-xs">{humanize(d.acquisitionMethod)}<br />{humanize(d.acquisitionStatus)}</Td>
                <Td className="text-right tabular-nums">{d.evidenceCount}</Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      </Card>

      <Card>
        <CardHeader title="3. Evidence sources and integrity" />
        {s.evidenceSources.length === 0 ? <CardBody><p className="text-sm text-slate-500">No evidence files were imported.</p></CardBody> : (
          <ul className="divide-y divide-slate-100">
            {s.evidenceSources.map((f) => (
              <li key={f.id} className="px-5 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-slate-900">File #{f.id}: {f.originalFileName}</span>
                  <StatusBadge value={f.processingStatus} />
                  <StatusBadge value={f.integrityStatus} />
                </div>
                <div className="mt-1"><HashValue value={f.sha256} /></div>
                <p className="mt-1 text-xs text-slate-500">
                  {f.sourceFormat} · {formatBytes(f.sizeBytes)} · imported {formatUtc(f.importedAt)} by {f.importedBy} · {f.recordsExtracted} record(s), {f.duplicatesSkipped} duplicate(s), {f.timestampIssues} timestamp issue(s)
                  {f.lastVerifiedAt ? ` · last verified ${formatUtc(f.lastVerifiedAt)} by ${f.lastVerifiedBy}` : ' · never re-verified'}
                </p>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <CardHeader title="4. Evidence overview" />
        <CardBody>
          <KeyValueList items={[
            { label: 'Total records', value: o.totalRecords },
            { label: 'Time span (UTC)', value: o.firstEventTime ? `${formatUtc(o.firstEventTime)} → ${formatUtc(o.lastEventTime)}` : '—' },
            { label: 'Records by type', value: Object.entries(o.recordsByType).map(([k, v]) => `${k}: ${v}`).join(', ') || '—' },
            { label: 'Timestamp derivation', value: Object.entries(o.recordsByTimestampStatus).map(([k, v]) => `${humanize(k)}: ${v}`).join(', ') || '—' },
            { label: 'Undated records', value: o.undatedRecords },
            { label: 'Records without source file', value: o.recordsWithoutSourceFile }
          ]} />
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="5. Findings (chronological narrative)" />
        {s.findings.length === 0 ? <CardBody><p className="text-sm text-slate-500">No findings were recorded.</p></CardBody> : (
          <ol className="divide-y divide-slate-100">
            {s.findings.map((f, index) => (
              <li key={f.id} className={`px-5 py-4 ${f.aiGenerated && f.reviewStatus !== 'REVIEWED' ? 'bg-amber-50/60' : ''}`}>
                <p className="text-sm font-semibold text-slate-900">5.{index + 1} {f.title}</p>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  <StatusBadge value={f.basis} label={BASIS_LABELS[f.basis]} />
                  <StatusBadge value={f.reviewStatus} />
                  <Badge>{humanize(f.significance)} significance</Badge>
                  {f.aiGenerated && <Badge tone="brand">AI-assisted{f.aiEngine ? ` · ${f.aiEngine}` : ''}</Badge>}
                </div>
                {f.aiGenerated && f.reviewStatus !== 'REVIEWED' && <p className="mt-2 text-xs font-medium text-amber-800">Not reviewed: shown for transparency only.</p>}
                <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{f.description}</p>
                <p className="mt-1 text-xs text-slate-500">Recorded by {f.createdBy}{f.reviewedBy ? ` · ${humanize(f.reviewStatus).toLowerCase()} by ${f.reviewedBy}` : ''}</p>
                {f.evidence.length > 0 && (
                  <div className="mt-2 rounded-md border border-slate-200">
                    <Table minWidth={640}>
                      <thead><tr><Th>Record</Th><Th>Time</Th><Th>Content</Th><Th>Source</Th></tr></thead>
                      <tbody>{f.evidence.map((item) => <RefRow key={item.evidenceId} item={item} />)}</tbody>
                    </Table>
                  </div>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card>

      <Card>
        <CardHeader title={`6. ${s.fullTimelineIncluded ? 'Timeline (all records)' : 'Key events (cited or bookmarked)'}`} />
        {s.keyEvents.length === 0 ? <CardBody><p className="text-sm text-slate-500">No events selected.</p></CardBody> : (
          <Table minWidth={700}>
            <thead><tr><Th>Record</Th><Th>Time</Th><Th>Content</Th><Th>Source</Th></tr></thead>
            <tbody>{s.keyEvents.map((event) => <RefRow key={event.evidence.evidenceId} item={event.evidence} />)}</tbody>
          </Table>
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="7. Methodology" />
          <CardBody><ol className="list-decimal space-y-2 pl-5 text-sm text-slate-700">{s.methodology.map((item, i) => <li key={i}>{item}</li>)}</ol></CardBody>
        </Card>
        <Card>
          <CardHeader title="8. Limitations" />
          <CardBody><ul className="list-disc space-y-2 pl-5 text-sm text-slate-700">{s.limitations.map((item, i) => <li key={i}>{item}</li>)}</ul></CardBody>
        </Card>
      </div>

      <Card>
        <CardHeader title="9. Audit information"
          description={`Audit hash chain at generation: ${s.auditChain.status} (${s.auditChain.recordsChecked} chained, ${s.auditChain.legacyUnchainedRecords} older unchained record(s)).`} />
        {s.auditTrail.length === 0 ? <CardBody><p className="text-sm text-slate-500">Audit trail not included.</p></CardBody> : (
          <Table minWidth={720}>
            <thead><tr><Th>#</Th><Th>Time</Th><Th>Actor</Th><Th>Action</Th><Th>Description</Th></tr></thead>
            <tbody>
              {s.auditTrail.map((a) => (
                <Tr key={a.id}>
                  <Td className="mono">{a.id}</Td>
                  <Td className="whitespace-nowrap text-xs">{formatUtc(a.timestamp)}</Td>
                  <Td>{a.actor ?? '—'}</Td>
                  <Td className="mono">{a.action}{!a.success && <span className="ml-1 text-red-600">(failed)</span>}</Td>
                  <Td className="text-xs">{a.description}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <p className="text-center text-xs text-slate-400"><Link to={`/cases/${c.id}`} className="link">Open case {c.caseNumber}</Link></p>
    </div>
  );
}
