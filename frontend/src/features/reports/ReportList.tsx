import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, Eye, Trash2 } from 'lucide-react';
import { downloadFile } from '../../services/api';
import { deleteReport } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { IconButton } from '../../components/ui/Button';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/Dialog';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { formatUtc, shortHash } from '../../lib/format';
import type { ReportSummary } from '../../types/api';

export function ReportList({ reports, showCase = false }: { reports: ReportSummary[]; showCase?: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { canWrite } = usePermissions();
  const [pendingDelete, setPendingDelete] = useState<ReportSummary | null>(null);
  const remove = useMutation({
    mutationFn: (id: number) => deleteReport(id),
    onSuccess: () => {
      toast.success('Report deleted.');
      setPendingDelete(null);
      void queryClient.invalidateQueries({ queryKey: ['reports'] });
      void queryClient.invalidateQueries({ queryKey: ['case'] });
    },
    onError: (error) => { toast.error(friendlyError(error)); setPendingDelete(null); }
  });

  async function pdf(report: ReportSummary) {
    try {
      await downloadFile(`/reports/${report.id}/pdf`, `${report.reportNumber ?? `report-${report.id}`}.pdf`);
    } catch (error) {
      toast.error(friendlyError(error));
    }
  }

  return (
    <>
      <Table minWidth={820}>
        <thead>
          <tr>
            <Th>Report</Th>
            {showCase && <Th>Case</Th>}
            <Th>Status</Th>
            <Th>Generated</Th>
            <Th>Content</Th>
            <Th>Snapshot SHA-256</Th>
            <Th className="text-right"><span className="sr-only">Actions</span></Th>
          </tr>
        </thead>
        <tbody>
          {reports.map((report) => (
            <Tr key={report.id}>
              <Td>
                <Link to={`/reports/${report.id}`} className="link">{report.reportNumber ?? `Report #${report.id}`}</Link>
                <p className="max-w-xs truncate text-xs text-slate-500">{report.title}</p>
              </Td>
              {showCase && <Td>{report.caseId ? <Link className="link" to={`/cases/${report.caseId}`}>{report.caseNumber}</Link> : '—'}</Td>}
              <Td>
                <div className="flex flex-col items-start gap-1">
                  <StatusBadge value={report.status} />
                  {!report.hasSnapshot && <Badge tone="warning">Legacy</Badge>}
                </div>
              </Td>
              <Td className="text-xs">
                <p>{formatUtc(report.generatedAt)}</p>
                <p className="text-slate-500">by {report.generatedBy}</p>
                {report.reviewedAt && <p className="text-slate-500">reviewed by {report.reviewedBy}</p>}
              </Td>
              <Td className="text-xs">
                {report.hasSnapshot ? (
                  <>
                    <p>{report.findingsCount} finding(s)</p>
                    {report.unreviewedAiFindings > 0 && <p className="text-amber-700">{report.unreviewedAiFindings} unreviewed AI-assisted</p>}
                  </>
                ) : '—'}
              </Td>
              <Td><code className="mono text-slate-600" title={report.contentSha256 ?? undefined}>{shortHash(report.contentSha256)}</code></Td>
              <Td className="text-right">
                <div className="flex justify-end gap-1">
                  <Link to={`/reports/${report.id}`} aria-label="View report" title="View report" className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-900"><Eye className="h-4 w-4" /></Link>
                  {report.hasSnapshot && <IconButton label="Download PDF" onClick={() => void pdf(report)}><Download className="h-4 w-4" /></IconButton>}
                  {canWrite && <IconButton label="Delete report" className="hover:text-red-600" onClick={() => setPendingDelete(report)}><Trash2 className="h-4 w-4" /></IconButton>}
                </div>
              </Td>
            </Tr>
          ))}
        </tbody>
      </Table>
      <ConfirmDialog open={pendingDelete !== null} title="Delete report?" confirmLabel="Delete report" loading={remove.isPending}
        message={<>Delete <strong>{pendingDelete?.reportNumber}</strong>? Evidence and findings are not affected. The deletion is audited.</>}
        onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)} onCancel={() => setPendingDelete(null)} />
    </>
  );
}
