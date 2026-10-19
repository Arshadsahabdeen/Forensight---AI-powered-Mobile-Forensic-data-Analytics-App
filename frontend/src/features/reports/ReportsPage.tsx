import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { FileText } from 'lucide-react';
import { getReports } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { SearchInput, Select } from '../../components/ui/Input';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { CasePicker } from '../../components/common/CasePicker';
import { ReportList } from './ReportList';

export default function ReportsPage() {
  const [caseId, setCaseId] = useState<number | undefined>();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const reports = useQuery({ queryKey: ['reports', caseId ?? 'all'], queryFn: () => getReports(caseId) });
  const term = search.trim().toLowerCase();
  const filtered = (reports.data ?? []).filter((report) =>
    (!term || `${report.reportNumber} ${report.title} ${report.caseNumber}`.toLowerCase().includes(term)) && (!status || report.status === status));

  return (
    <>
      <PageHeader title="Reports" description="Generated case reports. Generate new reports from a case's Reports tab." />
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-[1fr_280px_180px]">
          <SearchInput aria-label="Search reports" placeholder="Search report number, title or case…" value={search} onChange={(event) => setSearch(event.target.value)} />
          <CasePicker allowAll value={caseId} onChange={setCaseId} />
          <Select aria-label="Status" value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">All statuses</option>
            <option value="GENERATED">Generated</option>
            <option value="REVIEWED">Reviewed</option>
          </Select>
        </div>
        {reports.isLoading ? <SkeletonRows rows={5} columns={5} /> : reports.isError ? (
          <ErrorState message={friendlyError(reports.error)} onRetry={() => void reports.refetch()} />
        ) : filtered.length === 0 ? (
          <EmptyState icon={<FileText className="h-6 w-6" />} title={reports.data?.length ? 'No reports match' : 'No reports yet'} />
        ) : <ReportList reports={filtered} showCase />}
      </Card>
    </>
  );
}
