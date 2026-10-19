import { useQuery } from '@tanstack/react-query';
import { getCases } from '../../services/forensics';
import { Select } from '../ui/Input';

/** Select among cases the current user may view (the backend applies the access rules). */
export function CasePicker({ value, onChange, allowAll = false, id, className = '' }: {
  value: number | undefined;
  onChange: (caseId: number | undefined) => void;
  allowAll?: boolean;
  id?: string;
  className?: string;
}) {
  const cases = useQuery({
    queryKey: ['cases', 'picker'],
    queryFn: () => getCases({ page: 0, size: 100, sortBy: 'updatedAt', sortDir: 'desc' }),
    staleTime: 60_000
  });
  return (
    <Select id={id} aria-label="Case" className={className} value={value ?? ''} disabled={cases.isLoading}
      onChange={(event) => onChange(event.target.value ? Number(event.target.value) : undefined)}>
      <option value="">{cases.isLoading ? 'Loading cases…' : allowAll ? 'All accessible cases' : 'Select a case…'}</option>
      {cases.data?.items.map((item) => (
        <option key={item.id} value={item.id}>
          {item.caseNumber} — {item.title}
        </option>
      ))}
    </Select>
  );
}
