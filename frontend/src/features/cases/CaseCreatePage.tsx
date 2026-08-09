import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../../components/ui/Card';
import { CaseForm } from './CaseForm';
import type { CaseRequest } from '../../types/cases';
import { useAuth } from '../../contexts/AuthContext';
import { createCase } from '../../services/caseService';
import { friendlyError } from '../../services/errors';

export function CaseCreatePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, hasRole } = useAuth();
  const [message, setMessage] = useState('');

  const mutation = useMutation({
    mutationFn: createCase,
    onSuccess: async (created) => {
      setMessage('Case created successfully.');
      await queryClient.invalidateQueries({ queryKey: ['cases'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      window.setTimeout(() => navigate(`/cases/${created.id}`), 700);
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

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold text-white">Create Investigation Case</h1>
        <p className="mt-1 text-sm text-slate-400">Create a new forensic case record with validated fields.</p>
      </div>

      {(message || mutation.error) && (
        <div className={`rounded-md border px-4 py-3 text-sm ${mutation.error ? 'border-rose-400/30 bg-rose-400/10 text-rose-200' : 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200'}`}>
          {mutation.error ? friendlyError(mutation.error, 'Unable to create case.') : message}
        </div>
      )}

      <Card>
        <CaseForm
          isSubmitting={mutation.isPending}
          submitLabel="Create Case"
          onSubmit={handleSubmit}
          initialValues={{
            status: 'OPEN' as const,
            priority: 'MEDIUM' as const,
            leadInvestigator: hasRole(['INVESTIGATOR']) && !hasRole(['ADMIN']) ? user?.fullName ?? '' : ''
          }}
          disableLeadInvestigator={hasRole(['INVESTIGATOR']) && !hasRole(['ADMIN'])}
        />
      </Card>
    </div>
  );
}
