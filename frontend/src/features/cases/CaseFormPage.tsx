import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createCase, getCase, getInvestigators, updateCase } from '../../services/forensics';
import { fieldErrors, friendlyError } from '../../services/errors';
import { useAuth, usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, CardBody } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Field, Input, Select, Textarea } from '../../components/ui/Input';
import { Alert, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { validateCase } from './caseValidation';
import type { CaseRequest } from '../../types/api';

const EMPTY: CaseRequest = {
  caseNumber: '',
  title: '',
  description: '',
  status: 'OPEN',
  priority: 'MEDIUM',
  leadInvestigator: '',
  category: '',
  caseType: 'Mobile Forensics',
  location: '',
  notes: ''
};

export default function CaseFormPage() {
  const { id } = useParams();
  const caseId = id ? Number(id) : undefined;
  const editing = caseId !== undefined;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { user } = useAuth();
  const { isAdmin } = usePermissions();
  const [values, setValues] = useState<CaseRequest>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const existing = useQuery({ queryKey: ['case', caseId], queryFn: () => getCase(caseId!), enabled: editing });
  const investigators = useQuery({ queryKey: ['investigators'], queryFn: getInvestigators, staleTime: 5 * 60_000 });

  useEffect(() => {
    if (existing.data) {
      const o = existing.data.overview;
      setValues({ caseNumber: o.caseNumber, title: o.title, description: o.description, status: o.status, priority: o.priority,
        leadInvestigator: o.leadInvestigator, category: o.category, caseType: o.caseType, location: o.location ?? '', notes: o.notes ?? '' });
    } else if (!editing && user && !isAdmin) {
      setValues((current) => ({ ...current, leadInvestigator: user.fullName }));
    }
  }, [existing.data, editing, user, isAdmin]);

  const save = useMutation({
    mutationFn: (body: CaseRequest) => (editing ? updateCase(caseId!, body) : createCase(body)),
    onSuccess: (saved) => {
      toast.success(editing ? 'Case updated.' : 'Case created.');
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
      void queryClient.invalidateQueries({ queryKey: ['case', saved.id] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      navigate(`/cases/${saved.id}`);
    },
    onError: (error) => setErrors({ form: friendlyError(error), ...fieldErrors(error) })
  });

  function set<K extends keyof CaseRequest>(key: K, value: CaseRequest[K]) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: '' }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const next = validateCase(values);
    setErrors(next as Record<string, string>);
    if (Object.keys(next).length) return;
    save.mutate({ ...values, caseNumber: values.caseNumber.trim(), title: values.title.trim(), description: values.description.trim() });
  }

  if (editing && existing.isLoading) return <Skeleton className="h-96" />;
  if (editing && existing.isError) return <Card><ErrorState message={friendlyError(existing.error)} /></Card>;
  if (editing && existing.data && !existing.data.canEdit) {
    return <Alert tone="warning" title="Read-only">You can view this case but not edit it.</Alert>;
  }

  const investigatorOptions = investigators.data ?? [];
  const leadMissing = values.leadInvestigator && !investigatorOptions.some((item) => item.fullName === values.leadInvestigator);

  return (
    <>
      <PageHeader title={editing ? 'Edit case' : 'New case'} back={{ to: editing ? `/cases/${caseId}` : '/cases', label: editing ? 'Back to case' : 'Cases' }} />
      <Card className="max-w-3xl">
        <CardBody>
          <form onSubmit={submit} noValidate className="space-y-5">
            {errors.form && <Alert tone="danger">{errors.form}</Alert>}
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Case number" required error={errors.caseNumber} hint="Unique reference, e.g. CR-2026-0042">
                {(props) => <Input {...props} value={values.caseNumber} onChange={(event) => set('caseNumber', event.target.value)} />}
              </Field>
              <Field label="Lead investigator" required error={errors.leadInvestigator}
                hint={!isAdmin ? 'Investigators lead the cases they open. Administrators can reassign.' : undefined}>
                {(props) => (
                  <Select {...props} value={values.leadInvestigator} disabled={!isAdmin} onChange={(event) => set('leadInvestigator', event.target.value)}>
                    <option value="">Select…</option>
                    {leadMissing && <option value={values.leadInvestigator}>{values.leadInvestigator}</option>}
                    {investigatorOptions.map((item) => <option key={item.username} value={item.fullName}>{item.fullName} (@{item.username})</option>)}
                  </Select>
                )}
              </Field>
            </div>
            <Field label="Title" required error={errors.title}>
              {(props) => <Input {...props} maxLength={180} value={values.title} onChange={(event) => set('title', event.target.value)} />}
            </Field>
            <Field label="Description" required error={errors.description}>
              {(props) => <Textarea {...props} rows={4} maxLength={2000} value={values.description} onChange={(event) => set('description', event.target.value)} />}
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Status" required>
                {(props) => (
                  <Select {...props} value={values.status} onChange={(event) => set('status', event.target.value as CaseRequest['status'])}>
                    <option value="OPEN">Open</option>
                    <option value="ACTIVE">Active</option>
                    <option value="ON_HOLD">On hold</option>
                    <option value="CLOSED">Closed</option>
                    <option value="ARCHIVED">Archived</option>
                  </Select>
                )}
              </Field>
              <Field label="Priority" required>
                {(props) => (
                  <Select {...props} value={values.priority} onChange={(event) => set('priority', event.target.value as CaseRequest['priority'])}>
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </Select>
                )}
              </Field>
              <Field label="Category" required error={errors.category} hint="e.g. Financial crime, Cybercrime">
                {(props) => <Input {...props} maxLength={120} value={values.category} onChange={(event) => set('category', event.target.value)} />}
              </Field>
              <Field label="Case type" required error={errors.caseType}>
                {(props) => <Input {...props} maxLength={120} value={values.caseType} onChange={(event) => set('caseType', event.target.value)} />}
              </Field>
            </div>
            <Field label="Location" error={errors.location}>
              {(props) => <Input {...props} maxLength={255} value={values.location} onChange={(event) => set('location', event.target.value)} />}
            </Field>
            <Field label="Notes" error={errors.notes}>
              {(props) => <Textarea {...props} rows={3} maxLength={4000} value={values.notes} onChange={(event) => set('notes', event.target.value)} />}
            </Field>
            <div className="flex justify-end gap-2 border-t border-slate-200 pt-4">
              <Button variant="secondary" onClick={() => navigate(-1)} disabled={save.isPending}>Cancel</Button>
              <Button type="submit" loading={save.isPending}>{editing ? 'Save changes' : 'Create case'}</Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </>
  );
}
