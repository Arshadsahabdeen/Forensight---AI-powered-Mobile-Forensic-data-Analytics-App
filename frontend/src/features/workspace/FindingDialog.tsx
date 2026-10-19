import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createFinding, updateFinding } from '../../services/forensics';
import { fieldErrors, friendlyError } from '../../services/errors';
import { useToast } from '../../components/ui/Toast';
import { Dialog } from '../../components/ui/Dialog';
import { Button } from '../../components/ui/Button';
import { Checkbox, Field, Input, Select, Textarea } from '../../components/ui/Input';
import { Alert } from '../../components/ui/Feedback';
import type { Finding, FindingBasis, FindingRequest, Significance } from '../../types/api';

export function parseIds(text: string): number[] | null {
  const parts = text.split(/[\s,;]+/).map((part) => part.replace(/^#/, '')).filter(Boolean);
  const ids = parts.map(Number);
  if (ids.some((id) => !Number.isInteger(id) || id <= 0)) return null;
  return Array.from(new Set(ids));
}

export function FindingDialog({ open, onClose, caseId, finding, initialEvidenceIds = [], initialTitle = '', initialDescription = '', aiEngine }: {
  open: boolean;
  onClose: () => void;
  caseId: number;
  finding?: Finding;
  initialEvidenceIds?: number[];
  initialTitle?: string;
  initialDescription?: string;
  /** Set when the content comes from the assistant: the finding is saved as AI-generated and needs review. */
  aiEngine?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const aiGenerated = finding ? finding.aiGenerated : Boolean(aiEngine);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [basis, setBasis] = useState<FindingBasis>('VERIFIED_FACT');
  const [significance, setSignificance] = useState<Significance>('MEDIUM');
  const [evidenceText, setEvidenceText] = useState('');
  const [includeInReport, setIncludeInReport] = useState(true);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    setTitle(finding?.title ?? initialTitle);
    setDescription(finding?.description ?? initialDescription);
    setBasis(finding?.basis ?? (aiGenerated ? 'AI_SUGGESTED' : 'VERIFIED_FACT'));
    setSignificance(finding?.significance ?? 'MEDIUM');
    setEvidenceText((finding ? finding.evidence.map((e) => e.id).concat(finding.missingEvidenceIds) : initialEvidenceIds).join(', '));
    setIncludeInReport(finding?.includeInReport ?? true);
    setErrors({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, finding]);

  const save = useMutation({
    mutationFn: (body: FindingRequest) => (finding ? updateFinding(caseId, finding.id, body) : createFinding(caseId, body)),
    onSuccess: () => {
      toast.success(finding ? 'Finding updated.' : aiGenerated ? 'AI-generated finding saved. It needs review before it counts as reviewed.' : 'Finding added.');
      void queryClient.invalidateQueries({ queryKey: ['workspace', caseId] });
      void queryClient.invalidateQueries({ queryKey: ['evidence'] });
      void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
      onClose();
    },
    onError: (error) => setErrors({ form: friendlyError(error), ...fieldErrors(error) })
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = 'Enter a short title.';
    if (!description.trim()) next.description = 'Describe the finding.';
    const ids = parseIds(evidenceText);
    if (ids === null) next.evidenceIds = 'Use evidence record numbers separated by commas, e.g. 12, 15.';
    if (basis === 'VERIFIED_FACT' && ids && ids.length === 0) next.evidenceIds = 'A verified fact must cite at least one evidence record.';
    setErrors(next);
    if (Object.keys(next).length) return;
    save.mutate({
      title: title.trim(),
      description: description.trim(),
      basis,
      significance,
      evidenceIds: ids ?? [],
      includeInReport,
      narrativeOrder: finding?.narrativeOrder ?? null,
      aiGenerated,
      aiEngine: aiEngine ?? finding?.aiEngine ?? null
    });
  }

  return (
    <Dialog open={open} onClose={onClose} title={finding ? 'Edit finding' : 'Add finding'} size="lg"
      description={aiGenerated ? 'AI-assisted content. It is labelled in reports and must be reviewed by someone other than you.' : 'Findings appear in the case narrative and reports.'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" form="finding-form" loading={save.isPending}>{finding ? 'Save changes' : 'Add finding'}</Button>
        </>
      }>
      <form id="finding-form" onSubmit={submit} noValidate className="space-y-4">
        {errors.form && <Alert tone="danger">{errors.form}</Alert>}
        {finding?.reviewStatus === 'REVIEWED' && <Alert tone="warning">Saving changes clears the existing review.</Alert>}
        <Field label="Title" required error={errors.title}>
          {(props) => <Input {...props} maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} />}
        </Field>
        <Field label="Description" required error={errors.description} hint="State what the records show. Keep interpretation separate from fact.">
          {(props) => <Textarea {...props} rows={5} value={description} onChange={(event) => setDescription(event.target.value)} />}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Basis" required hint={aiGenerated ? 'Fixed for AI-assisted findings.' : undefined}>
            {(props) => (
              <Select {...props} value={basis} disabled={aiGenerated} onChange={(event) => setBasis(event.target.value as FindingBasis)}>
                <option value="VERIFIED_FACT">Verified fact — stated directly by cited records</option>
                <option value="INFERENCE">Inference — interpretation to be verified</option>
                {aiGenerated && <option value="AI_SUGGESTED">AI-suggested</option>}
              </Select>
            )}
          </Field>
          <Field label="Significance" required>
            {(props) => (
              <Select {...props} value={significance} onChange={(event) => setSignificance(event.target.value as Significance)}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </Select>
            )}
          </Field>
        </div>
        <Field label="Supporting evidence records" error={errors.evidenceIds} hint="Record numbers from this case, separated by commas.">
          {(props) => <Input {...props} value={evidenceText} onChange={(event) => setEvidenceText(event.target.value)} placeholder="e.g. 12, 15, 18" />}
        </Field>
        <Checkbox label="Include in reports and narrative" checked={includeInReport} onChange={(event) => setIncludeInReport(event.target.checked)} />
      </form>
    </Dialog>
  );
}
