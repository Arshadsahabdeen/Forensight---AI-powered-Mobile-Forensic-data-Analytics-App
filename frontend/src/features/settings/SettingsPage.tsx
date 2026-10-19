import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAssistantStatus, getSettings, updateSettings } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card, CardBody, CardHeader, KeyValueList } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Checkbox, Field, Input } from '../../components/ui/Input';
import { Alert, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { Badge } from '../../components/ui/Badge';
import type { PlatformSettings } from '../../types/api';

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings });
  const assistant = useQuery({ queryKey: ['assistant-status'], queryFn: getAssistantStatus });
  const [form, setForm] = useState<PlatformSettings | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (settings.data) setForm(settings.data);
  }, [settings.data]);

  const save = useMutation({
    mutationFn: updateSettings,
    onSuccess: (data) => {
      toast.success('Settings saved.');
      queryClient.setQueryData(['settings'], data);
      void queryClient.invalidateQueries({ queryKey: ['session-policy'] });
    },
    onError: (error) => setErrors({ form: friendlyError(error) })
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!form) return;
    const next: Record<string, string> = {};
    if (!(form.sessionTimeoutMinutes >= 5 && form.sessionTimeoutMinutes <= 1440)) next.sessionTimeoutMinutes = 'Between 5 and 1440 minutes.';
    if (!(form.defaultPageSize >= 10 && form.defaultPageSize <= 100)) next.defaultPageSize = 'Between 10 and 100.';
    if (!(form.auditRetentionDays >= 30 && form.auditRetentionDays <= 3650)) next.auditRetentionDays = 'Between 30 and 3650 days.';
    setErrors(next);
    if (!Object.keys(next).length) save.mutate(form);
  }

  const set = <K extends keyof PlatformSettings>(key: K, value: PlatformSettings[K]) => setForm((current) => (current ? { ...current, [key]: value } : current));

  return (
    <>
      <PageHeader title="Settings" description="Platform controls enforced by the backend." />
      <div className="grid max-w-5xl gap-6">
        <Card>
          <CardHeader title="Platform controls" />
          <CardBody>
            {settings.isLoading && <Skeleton className="h-40" />}
            {settings.isError && <ErrorState message={friendlyError(settings.error)} onRetry={() => void settings.refetch()} />}
            {form && (
              <form onSubmit={submit} noValidate className="space-y-5">
                {errors.form && <Alert tone="danger">{errors.form}</Alert>}
                <div className="grid gap-4 sm:grid-cols-3">
                  <Field label="Idle session timeout (minutes)" error={errors.sessionTimeoutMinutes}
                    hint="Users are signed out after this much inactivity. The server also refuses to renew idle sessions.">
                    {(p) => <Input {...p} type="number" min={5} max={1440} value={form.sessionTimeoutMinutes} onChange={(e) => set('sessionTimeoutMinutes', Number(e.target.value))} />}
                  </Field>
                  <Field label="Default page size" error={errors.defaultPageSize}>
                    {(p) => <Input {...p} type="number" min={10} max={100} value={form.defaultPageSize} onChange={(e) => set('defaultPageSize', Number(e.target.value))} />}
                  </Field>
                  <Field label="Audit retention policy (days)" error={errors.auditRetentionDays}
                    hint="Recorded policy value. The platform never deletes audit records automatically.">
                    {(p) => <Input {...p} type="number" min={30} max={3650} value={form.auditRetentionDays} onChange={(e) => set('auditRetentionDays', Number(e.target.value))} />}
                  </Field>
                </div>
                <Checkbox label="Maintenance mode" description="While enabled, only administrators can make changes; everyone else has read-only access."
                  checked={form.maintenanceMode} onChange={(e) => set('maintenanceMode', e.target.checked)} />
                <div className="flex justify-end border-t border-slate-200 pt-4">
                  <Button type="submit" loading={save.isPending}>Save settings</Button>
                </div>
              </form>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="AI assistant configuration" description="Configured through environment variables on the AI service; secrets are never shown here." />
          <CardBody className="space-y-3">
            {assistant.isLoading && <Skeleton className="h-16" />}
            {assistant.data && (
              <>
                <div className="flex flex-wrap gap-2">
                  <Badge tone={assistant.data.reachable ? 'success' : 'danger'}>{assistant.data.reachable ? 'Reachable' : 'Unreachable'}</Badge>
                  <Badge tone={assistant.data.engine === 'llm' ? 'brand' : 'neutral'}>Engine: {assistant.data.engine}</Badge>
                  <Badge>{assistant.data.externalProcessingAllowed ? 'External processing allowed' : 'Evidence stays local'}</Badge>
                </div>
                <p className="text-sm text-slate-600">{assistant.data.message}</p>
                <KeyValueList items={[
                  { label: 'AI service URL (backend)', value: <code className="mono">{assistant.data.serviceUrl}</code> },
                  { label: 'Model', value: assistant.data.model ?? 'None (extractive engine)' }
                ]} />
                <div className="rounded-md bg-slate-50 p-3 text-xs text-slate-600">
                  To enable generative analysis set <code className="mono">ANTHROPIC_API_KEY</code> and <code className="mono">AI_ALLOW_EXTERNAL_PROCESSING=true</code> on the AI service, then restart it.
                  Only do this where policy allows evidence to be sent to the model provider.
                </div>
              </>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Security posture" />
          <CardBody>
            <KeyValueList items={[
              { label: 'Authentication', value: 'Short-lived JWT access tokens with rotating, server-side refresh tokens' },
              { label: 'Authorization', value: 'Role and case-assignment checks enforced on every API call' },
              { label: 'Audit trail', value: 'Append-only (database trigger) and SHA-256 hash-chained' },
              { label: 'Evidence storage', value: 'Originals stored read-only, content-addressed, SHA-256 recorded at import' },
              { label: 'Frontend API base URL', value: <code className="mono">{import.meta.env.VITE_API_BASE_URL || '/api/v1'}</code> }
            ]} />
          </CardBody>
        </Card>
      </div>
    </>
  );
}
