import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Bot, FilePlus2, Sparkles } from 'lucide-react';
import { askAssistant, getAssistantStatus } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Field, Select, Textarea } from '../../components/ui/Input';
import { Alert, EmptyState, Skeleton } from '../../components/ui/Feedback';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { formatUtc, humanize } from '../../lib/format';
import { EvidenceDrawer } from '../evidence/EvidenceDrawer';
import { FindingDialog } from '../workspace/FindingDialog';
import type { AssistantResponse, AssistantTask, CaseDetail } from '../../types/api';

const TASKS: { value: AssistantTask; label: string; hint: string; needsQuestion: boolean }[] = [
  { value: 'SEARCH', label: 'Search evidence', hint: 'Find records relevant to a description, number or name.', needsQuestion: true },
  { value: 'ASK', label: 'Ask a question', hint: 'Answer only from matching records, with citations.', needsQuestion: true },
  { value: 'SUMMARIZE', label: 'Summarize', hint: 'Counts, time span, frequent identifiers and data gaps.', needsQuestion: false },
  { value: 'ENTITIES', label: 'Extract entities', hint: 'Phone numbers, accounts, names, places and links.', needsQuestion: false }
];

const KIND_LABEL: Record<string, string> = { RECORD_FACT: 'Record', AGGREGATE: 'Aggregate', INFERENCE: 'Inference — verify' };

export function AssistantPanel({ caseId, canCreateFindings, devices }: { caseId: number; canCreateFindings: boolean; devices: Pick<CaseDetail['devices'][number], 'id' | 'deviceName'>[] }) {
  const [task, setTask] = useState<AssistantTask>('SEARCH');
  const [question, setQuestion] = useState('');
  const [deviceId, setDeviceId] = useState('');
  const [error, setError] = useState('');
  const [evidenceId, setEvidenceId] = useState<number | null>(null);
  const [findingFrom, setFindingFrom] = useState<{ title: string; description: string; ids: number[] } | null>(null);
  const status = useQuery({ queryKey: ['assistant-status'], queryFn: getAssistantStatus, staleTime: 60_000 });
  const definition = TASKS.find((item) => item.value === task)!;

  const run = useMutation({
    mutationFn: () => askAssistant(caseId, { task, question: question.trim() || undefined, deviceId: deviceId ? Number(deviceId) : undefined }),
    onError: (err) => setError(friendlyError(err))
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (definition.needsQuestion && !question.trim()) {
      setError('Enter a question or search text.');
      return;
    }
    setError('');
    run.mutate();
  }

  const result = run.data;

  return (
    <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
      <div className="space-y-4">
        <Card>
          <CardHeader title="Evidence assistant" description="Works only from this case's records and cites them." />
          <CardBody>
            <form onSubmit={submit} className="space-y-4" noValidate>
              <Field label="Task" hint={definition.hint}>
                {(props) => (
                  <Select {...props} value={task} onChange={(event) => { setTask(event.target.value as AssistantTask); setError(''); }}>
                    {TASKS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                  </Select>
                )}
              </Field>
              <Field label={definition.needsQuestion ? 'Question or search text' : 'Focus (optional)'} error={error || undefined}>
                {(props) => (
                  <Textarea {...props} rows={3} maxLength={1000} value={question} onChange={(event) => setQuestion(event.target.value)}
                    placeholder={task === 'ASK' ? 'e.g. When was the pickup arranged and by which number?' : 'e.g. north gate, +91 98450 11223'} />
                )}
              </Field>
              <Field label="Device">
                {(props) => (
                  <Select {...props} value={deviceId} onChange={(event) => setDeviceId(event.target.value)}>
                    <option value="">All devices in the case</option>
                    {devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
                  </Select>
                )}
              </Field>
              <Button type="submit" className="w-full" loading={run.isPending} icon={<Sparkles className="h-4 w-4" />}>Run</Button>
            </form>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Engine status" />
          <CardBody className="space-y-2 text-sm">
            {status.isLoading && <Skeleton className="h-12" />}
            {status.data && (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge value={status.data.reachable ? 'SUCCEEDED' : 'FAILED'} label={status.data.reachable ? 'AI service reachable' : 'AI service unreachable'} />
                  <Badge tone={status.data.engine === 'llm' ? 'brand' : 'neutral'}>{status.data.engine === 'llm' ? `Generative (${status.data.model})` : humanize(status.data.engine)}</Badge>
                </div>
                <p className="text-xs text-slate-600">{status.data.message}</p>
                {status.data.engine === 'llm' && <p className="text-xs text-amber-800">Selected evidence records are sent to the configured external model provider.</p>}
              </>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="space-y-4">
        {!result && !run.isPending && (
          <Card><EmptyState icon={<Bot className="h-6 w-6" />} title="No results yet" description="Choose a task and run it. Every statement links to the records it is based on." /></Card>
        )}
        {run.isPending && <Card><CardBody><Skeleton className="mb-3 h-5 w-1/3" /><Skeleton className="h-24" /></CardBody></Card>}
        {result && <AssistantResult result={result} canCreateFindings={canCreateFindings} onOpenEvidence={setEvidenceId} onCreateFinding={setFindingFrom} />}
      </div>

      <EvidenceDrawer evidenceId={evidenceId} onClose={() => setEvidenceId(null)} onSelect={setEvidenceId} />
      <FindingDialog open={findingFrom !== null} onClose={() => setFindingFrom(null)} caseId={caseId}
        initialTitle={findingFrom?.title} initialDescription={findingFrom?.description} initialEvidenceIds={findingFrom?.ids}
        aiEngine={result ? `${result.engine}${result.model ? ` / ${result.model}` : ''}` : undefined} />
    </div>
  );
}

function AssistantResult({ result, canCreateFindings, onOpenEvidence, onCreateFinding }: {
  result: AssistantResponse;
  canCreateFindings: boolean;
  onOpenEvidence: (id: number) => void;
  onCreateFinding: (value: { title: string; description: string; ids: number[] }) => void;
}) {
  const citedIds = Array.from(new Set([...result.citations.map((c) => c.evidenceId), ...result.statements.flatMap((s) => s.evidenceIds)]));
  return (
    <>
      <Card>
        <CardHeader
          title={humanize(result.task)}
          description={`Engine: ${result.engine}${result.model ? ` (${result.model})` : ''} · ${result.evidenceConsidered} of ${result.evidenceAvailable} record(s) considered · ${formatUtc(result.generatedAt)}`}
          actions={canCreateFindings && citedIds.length > 0 && result.status !== 'NO_EVIDENCE' && (
            <Button size="sm" variant="secondary" icon={<FilePlus2 className="h-4 w-4" />}
              onClick={() => onCreateFinding({ title: `Assistant: ${humanize(result.task)}`, description: result.answer, ids: citedIds.slice(0, 200) })}>
              Save as finding
            </Button>
          )}
        />
        <CardBody className="space-y-3">
          {result.status === 'FALLBACK' && <Alert tone="warning" title="AI analysis unavailable">Results below are keyword matches from the backend, not AI interpretation.</Alert>}
          {result.status === 'NO_EVIDENCE' && <Alert tone="info">{result.answer}</Alert>}
          {result.status !== 'NO_EVIDENCE' && (
            <div className={`rounded-md p-3 text-sm ${result.answerIsAiGenerated ? 'border border-brand-200 bg-brand-50/50' : 'bg-slate-50'}`}>
              {result.answerIsAiGenerated && <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-brand-800">AI-generated — verify against cited records</p>}
              <p className="whitespace-pre-wrap text-slate-800">{result.answer}</p>
            </div>
          )}
          {result.droppedUngroundedItems > 0 && (
            <p className="text-xs text-amber-800">{result.droppedUngroundedItems} item(s) were removed because they were not supported by the supplied records.</p>
          )}
        </CardBody>
      </Card>

      {result.statements.length > 0 && (
        <Card>
          <CardHeader title="Statements" description="Each statement lists the records it is based on." />
          <ul className="divide-y divide-slate-100">
            {result.statements.map((statement, index) => (
              <li key={index} className="px-5 py-3">
                <div className="flex flex-wrap items-start gap-2">
                  <Badge tone={statement.kind === 'INFERENCE' ? 'warning' : statement.kind === 'AGGREGATE' ? 'info' : 'success'}>{KIND_LABEL[statement.kind]}</Badge>
                  <p className="flex-1 text-sm text-slate-800">{statement.text}</p>
                </div>
                <div className="mt-1.5 flex flex-wrap gap-1">
                  {statement.evidenceIds.slice(0, 20).map((id) => (
                    <button key={id} type="button" onClick={() => onOpenEvidence(id)} className="mono rounded border border-slate-200 px-1.5 py-0.5 text-brand-700 hover:bg-brand-50">#{id}</button>
                  ))}
                  {statement.evidenceIds.length > 20 && <span className="text-xs text-slate-500">+{statement.evidenceIds.length - 20} more</span>}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {result.citations.length > 0 && (
        <Card>
          <CardHeader title="Cited records" description="Snippets come from the stored records, not from the AI output." />
          <ul className="divide-y divide-slate-100">
            {result.citations.map((citation) => (
              <li key={citation.evidenceId}>
                <button type="button" onClick={() => onOpenEvidence(citation.evidenceId)} className="w-full px-5 py-3 text-left hover:bg-slate-50">
                  <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                    <span className="mono text-brand-700">#{citation.evidenceId}</span>
                    <Badge>{citation.artifactType ?? 'Record'}</Badge>
                    <span>{citation.eventTime ? formatUtc(citation.eventTime) : 'Undated'}</span>
                    <span>{citation.deviceName}</span>
                  </div>
                  {citation.snippet && <p className="mt-1 text-sm text-slate-800">{citation.snippet}</p>}
                  {citation.reason && <p className="mt-0.5 text-xs text-slate-500">{citation.reason}</p>}
                </button>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {result.entities.length > 0 && (
        <Card>
          <CardHeader title="Entities" />
          <CardBody>
            <ul className="grid gap-2 sm:grid-cols-2">
              {result.entities.map((entity) => (
                <li key={`${entity.type}-${entity.value}`} className="rounded-md border border-slate-200 p-2.5">
                  <div className="flex items-center gap-2"><Badge tone="info">{entity.type}</Badge><span className="break-all text-sm text-slate-900">{entity.value}</span></div>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {entity.evidenceIds.slice(0, 10).map((id) => (
                      <button key={id} type="button" onClick={() => onOpenEvidence(id)} className="mono text-brand-700 hover:underline">#{id}</button>
                    ))}
                    {entity.evidenceIds.length > 10 && <span className="text-xs text-slate-500">+{entity.evidenceIds.length - 10}</span>}
                  </div>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      )}

      <Card>
        <CardBody className="space-y-2 text-xs text-slate-600">
          {result.limitations.map((item, index) => <p key={index}>• {item}</p>)}
          <p className="text-slate-500">{result.disclaimer}</p>
        </CardBody>
      </Card>
    </>
  );
}
