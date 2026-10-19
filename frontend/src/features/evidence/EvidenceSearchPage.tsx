import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../components/ui/PageHeader';
import { EvidenceBrowser } from './EvidenceBrowser';

export default function EvidenceSearchPage() {
  const [params] = useSearchParams();
  const caseParam = params.get('caseId');
  const initial = { search: params.get('search') ?? '', caseId: caseParam ? Number(caseParam) : undefined };
  return (
    <>
      <PageHeader title="Evidence search" description="Search extracted records across every case you can access. Select a record to see its provenance and related records." />
      <EvidenceBrowser showCaseFilter initialFilters={initial} />
    </>
  );
}
