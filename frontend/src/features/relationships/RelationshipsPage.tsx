import { GitBranch } from 'lucide-react';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { EmptyState, Skeleton } from '../../components/ui/Feedback';
import { CasePicker } from '../../components/common/CasePicker';
import { useSelectedCase } from '../timeline/TimelinePage';
import { RelationshipExplorer } from './RelationshipExplorer';

export default function RelationshipsPage() {
  const { caseId, detail, select } = useSelectedCase();
  return (
    <>
      <PageHeader title="Relationship explorer" description="People, identifiers, locations and devices connected by explainable rules, each backed by evidence records."
        actions={<CasePicker value={caseId} onChange={select} className="w-72" />} />
      {!caseId && <Card><EmptyState icon={<GitBranch className="h-6 w-6" />} title="Select a case" description="Choose a case to explore its relationships." /></Card>}
      {caseId && detail.isLoading && <Skeleton className="h-96" />}
      {caseId && detail.data && <RelationshipExplorer caseId={caseId} devices={detail.data.devices} />}
      {caseId && detail.isError && <Card><EmptyState title="This case is not available" description="It may not exist or you may not have access." /></Card>}
    </>
  );
}
