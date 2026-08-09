import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card } from '../../components/ui/Card';
import { CaseForm } from './CaseForm';
import type { CaseRequest } from '../../types/cases';
import { useAuth } from '../../contexts/AuthContext';
import { getCaseById, updateCase } from '../../services/caseService';
import { friendlyError } from '../../services/errors';

export function CaseEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const [message, setMessage] = useState('');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['case', id],
    queryFn: () => getCaseById(id!),
    enabled: Boolean(id)
  });

  const mutation = useMutation({
    mutationFn: (payload: CaseRequest) => updateCase(id!, payload),
    onSuccess: async (updated) => {
      setMessage('Case updated successfully.');
      await queryClient.invalidateQueries({ queryKey: ['case', id] });
      await queryClient.invalidateQueries({ queryKey: ['cases'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      window.setTimeout(() => navigate(`/cases/${updated.id}`), 700);
    }
  });

  async function handleSubmit(payload: CaseRequest) {
    setMessage('');
    try {
      await mutation.mutateAsync(payload);
    } catch {
      // Mutation error is rendered above the form.
    }
  }

  if (isLoading) {
    return <Card className="text-sm text-slate-300">Loading case for editing...</Card>;
  }

  if (error || !data) {
    return (
      <Card>
        <p className="text-sm text-rose-300">{friendlyError(error, 'Unable to load case for editing.')}</p>
        <button className="mt-3 rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-white/5" onClick={() => refetch()}>
          Retry
        </button>
      </Card>
    );
  }

  const overview = data.overview;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-white">Edit Case {overview.caseNumber}</h1>
        <p className="mt-1 text-sm text-slate-400">Update investigation details with role-aware access.</p>
      </div>

      {(message || mutation.error) && (
        <div className={`rounded-md border px-4 py-3 text-sm ${mutation.error ? 'border-rose-400/30 bg-rose-400/10 text-rose-200' : 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200'}`}>
          {mutation.error ? friendlyError(mutation.error, 'Unable to update case.') : message}
        </div>
      )}

      <Card>
        <CaseForm
          initialValues={{
            caseNumber: overview.caseNumber,
            title: overview.title,
            description: overview.description,
            status: overview.status,
            priority: overview.priority,
            leadInvestigator: overview.leadInvestigator,
            category: overview.category,
            caseType: overview.caseType,
            location: overview.location,
            notes: overview.notes
          }}
          isSubmitting={mutation.isPending}
          submitLabel="Save Changes"
          onSubmit={handleSubmit}
          disableLeadInvestigator={hasRole(['INVESTIGATOR']) && !hasRole(['ADMIN'])}
        />
      </Card>
    </div>
  );
}
