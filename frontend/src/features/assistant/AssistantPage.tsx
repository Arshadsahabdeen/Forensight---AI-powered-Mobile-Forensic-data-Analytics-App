import { Bot } from 'lucide-react';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { EmptyState, Skeleton } from '../../components/ui/Feedback';
import { CasePicker } from '../../components/common/CasePicker';
import { useSelectedCase } from '../timeline/TimelinePage';
import { AssistantPanel } from './AssistantPanel';

export default function AssistantPage() {
  const { caseId, detail, select } = useSelectedCase();
  return (
    <>
      <PageHeader title="Evidence assistant" description="Grounded search, summaries and entity extraction over a single case's records."
        actions={<CasePicker value={caseId} onChange={select} className="w-72" />} />
      {!caseId && <Card><EmptyState icon={<Bot className="h-6 w-6" />} title="Select a case" description="The assistant only works within one case at a time." /></Card>}
      {caseId && detail.isLoading && <Skeleton className="h-96" />}
      {caseId && detail.data && <AssistantPanel caseId={caseId} canCreateFindings={detail.data.canEdit} devices={detail.data.devices} />}
      {caseId && detail.isError && <Card><EmptyState title="This case is not available" description="It may not exist or you may not have access." /></Card>}
    </>
  );
}
