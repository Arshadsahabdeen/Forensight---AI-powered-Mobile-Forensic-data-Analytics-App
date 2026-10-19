import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileText } from 'lucide-react';
import { generateReport, getReports } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useToast } from '../../components/ui/Toast';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Checkbox, Field, Input } from '../../components/ui/Input';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { ReportList } from './ReportList';

export function CaseReportsPanel({ caseId, canEdit }: { caseId: number; canEdit: boolean }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [title, setTitle] = useState('');
  const [fullTimeline, setFullTimeline] = useState(false);
  const [auditTrail, setAuditTrail] = useState(true);
  const reports = useQuery({ queryKey: ['reports', caseId], queryFn: () => getReports(caseId) });

  const generate = useMutation({
    mutationFn: () => generateReport(caseId, { title: title.trim() || undefined, includeFullTimeline: fullTimeline, includeAuditTrail: auditTrail }),
    onSuccess: (report) => {
      toast.success(`Report ${report.reportNumber} generated.`);
      void queryClient.invalidateQueries({ queryKey: ['reports'] });
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
      navigate(`/reports/${report.id}`);
    },
    onError: (error) => toast.error(friendlyError(error))
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    generate.mutate();
  }

  return (
    <div className="space-y-6">
      {canEdit && (
        <Card>
          <CardHeader title="Generate report" description="Builds a report from the current case data. The data snapshot is stored with its SHA-256 so the report can be reproduced and checked later." />
          <CardBody>
            <form onSubmit={submit} className="space-y-4">
              <Field label="Title (optional)" hint="Defaults to the case title.">
                {(props) => <Input {...props} maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} />}
              </Field>
              <div className="space-y-2">
                <Checkbox label="Include the full timeline" description="Otherwise only records cited by findings or bookmarked are listed." checked={fullTimeline} onChange={(event) => setFullTimeline(event.target.checked)} />
                <Checkbox label="Include the case audit trail" checked={auditTrail} onChange={(event) => setAuditTrail(event.target.checked)} />
              </div>
              <Alert tone="info">Unreviewed findings, including AI-assisted ones, are included and clearly marked. Review them first for a final report.</Alert>
              <div className="flex justify-end">
                <Button type="submit" loading={generate.isPending} icon={<FileText className="h-4 w-4" />}>Generate report</Button>
              </div>
            </form>
          </CardBody>
        </Card>
      )}
      <Card>
        <CardHeader title="Reports for this case" />
        {reports.isLoading ? <SkeletonRows rows={3} columns={5} /> : reports.isError ? (
          <ErrorState message={friendlyError(reports.error)} onRetry={() => void reports.refetch()} />
        ) : reports.data && reports.data.length === 0 ? (
          <EmptyState icon={<FileText className="h-6 w-6" />} title="No reports yet" />
        ) : reports.data ? <ReportList reports={reports.data} /> : null}
      </Card>
    </div>
  );
}
