import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { getCase } from '../../services/forensics';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { EmptyState, Skeleton } from '../../components/ui/Feedback';
import { CasePicker } from '../../components/common/CasePicker';
import { TimelinePanel } from './TimelinePanel';

/** Shared shell for case-scoped analysis pages: pick a case, then render the panel. */
export function useSelectedCase() {
  const [params, setParams] = useSearchParams();
  const caseId = params.get('caseId') ? Number(params.get('caseId')) : undefined;
  const detail = useQuery({ queryKey: ['case', caseId], queryFn: () => getCase(caseId!), enabled: caseId !== undefined });
  const select = (id: number | undefined) => setParams(id ? { caseId: String(id) } : {}, { replace: true });
  return { caseId, detail, select };
}

export default function TimelinePage() {
  const { caseId, detail, select } = useSelectedCase();
  return (
    <>
      <PageHeader title="Timeline" description="Chronological view of a case's evidence, normalized to UTC with original timestamps preserved."
        actions={<CasePicker value={caseId} onChange={select} className="w-72" />} />
      {!caseId && <Card><EmptyState icon={<History className="h-6 w-6" />} title="Select a case" description="Choose a case to build its timeline." /></Card>}
      {caseId && detail.isLoading && <Skeleton className="h-64" />}
      {caseId && detail.data && <TimelinePanel caseId={caseId} devices={detail.data.devices} />}
      {caseId && detail.isError && <Card><EmptyState title="This case is not available" description="It may not exist or you may not have access." /></Card>}
    </>
  );
}
