import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bookmark, CheckCircle2, FileText, NotebookPen, Pencil, Plus, Send, Trash2, XCircle } from 'lucide-react';
import { createNote, deleteFinding, deleteNote, getWorkspace, removeBookmark, reviewFinding, submitFinding, updateNote } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useAuth, usePermissions } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { Card, CardHeader } from '../../components/ui/Card';
import { Button, IconButton } from '../../components/ui/Button';
import { Field, Input, Textarea } from '../../components/ui/Input';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { ConfirmDialog, Dialog } from '../../components/ui/Dialog';
import { Tabs } from '../../components/ui/Tabs';
import { BASIS_LABELS, formatRelative, formatUtc, humanize } from '../../lib/format';
import { EvidenceDrawer } from '../evidence/EvidenceDrawer';
import { FindingDialog } from './FindingDialog';
import type { Finding, Note } from '../../types/api';

export function WorkspacePanel({ caseId, canEdit }: { caseId: number; canEdit: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { user } = useAuth();
  const { canReview } = usePermissions();
  const [section, setSection] = useState('narrative');
  const [editingFinding, setEditingFinding] = useState<Finding | undefined>();
  const [findingDialog, setFindingDialog] = useState(false);
  const [reviewing, setReviewing] = useState<{ finding: Finding; decision: 'REVIEWED' | 'REJECTED' } | null>(null);
  const [deleting, setDeleting] = useState<{ kind: 'finding' | 'note'; id: number; title: string } | null>(null);
  const [noteDialog, setNoteDialog] = useState<{ note?: Note } | null>(null);
  const [evidenceId, setEvidenceId] = useState<number | null>(null);

  const workspace = useQuery({ queryKey: ['workspace', caseId], queryFn: () => getWorkspace(caseId) });
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['workspace', caseId] });
    void queryClient.invalidateQueries({ queryKey: ['case', caseId] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const submit = useMutation({
    mutationFn: (id: number) => submitFinding(caseId, id),
    onSuccess: () => { toast.success('Submitted for review.'); invalidate(); },
    onError: (error) => toast.error(friendlyError(error))
  });
  const remove = useMutation({
    mutationFn: async (target: { kind: 'finding' | 'note'; id: number }) => (target.kind === 'finding' ? deleteFinding(caseId, target.id) : deleteNote(caseId, target.id)),
    onSuccess: () => { toast.success('Deleted.'); setDeleting(null); invalidate(); },
    onError: (error) => { toast.error(friendlyError(error)); setDeleting(null); }
  });
  const unbookmark = useMutation({
    mutationFn: (id: number) => removeBookmark(caseId, id),
    onSuccess: () => { toast.success('Bookmark removed.'); invalidate(); },
    onError: (error) => toast.error(friendlyError(error))
  });

  if (workspace.isLoading) return <Card><SkeletonRows rows={6} columns={3} /></Card>;
  if (workspace.isError || !workspace.data) return <Card><ErrorState message={friendlyError(workspace.error)} onRetry={() => void workspace.refetch()} /></Card>;
  const data = workspace.data;

  const findingCard = (finding: Finding, numbered?: number) => {
    const own = finding.createdBy === user?.username;
    return (
      <li key={finding.id} className="px-5 py-4">
        <div className="flex flex-wrap items-start gap-2">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-slate-900">{numbered !== undefined && <span className="mr-1 text-slate-400">{numbered}.</span>}{finding.title}</p>
            <div className="mt-1 flex flex-wrap items-center gap-1.5">
              <StatusBadge value={finding.basis} label={BASIS_LABELS[finding.basis]} />
              <StatusBadge value={finding.reviewStatus} />
              <Badge>{humanize(finding.significance)} significance</Badge>
              {finding.aiGenerated && <Badge tone="brand">AI-assisted{finding.aiEngine ? ` · ${finding.aiEngine}` : ''}</Badge>}
              {!finding.includeInReport && <Badge>Excluded from report</Badge>}
            </div>
          </div>
          <div className="flex flex-wrap gap-1">
            {canEdit && finding.reviewStatus === 'DRAFT' && (
              <Button size="sm" variant="secondary" icon={<Send className="h-3.5 w-3.5" />} loading={submit.isPending && submit.variables === finding.id} onClick={() => submit.mutate(finding.id)}>Submit for review</Button>
            )}
            {canReview && finding.reviewStatus === 'NEEDS_REVIEW' && !own && (
              <>
                <Button size="sm" variant="secondary" icon={<CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />} onClick={() => setReviewing({ finding, decision: 'REVIEWED' })}>Approve</Button>
                <Button size="sm" variant="secondary" icon={<XCircle className="h-3.5 w-3.5 text-red-600" />} onClick={() => setReviewing({ finding, decision: 'REJECTED' })}>Reject</Button>
              </>
            )}
            {canEdit && <IconButton label="Edit finding" onClick={() => { setEditingFinding(finding); setFindingDialog(true); }}><Pencil className="h-4 w-4" /></IconButton>}
            {canEdit && <IconButton label="Delete finding" className="hover:text-red-600" onClick={() => setDeleting({ kind: 'finding', id: finding.id, title: finding.title })}><Trash2 className="h-4 w-4" /></IconButton>}
          </div>
        </div>
        <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{finding.description}</p>
        {finding.evidence.length > 0 ? (
          <div className="mt-2 flex flex-wrap items-center gap-1 text-xs">
            <span className="text-slate-500">Evidence:</span>
            {finding.evidence.map((e) => (
              <button key={e.id} type="button" onClick={() => setEvidenceId(e.id)} className="mono rounded border border-slate-200 px-1.5 py-0.5 text-brand-700 hover:bg-brand-50"
                title={`${e.artifactType} ${e.eventTime ? formatUtc(e.eventTime) : 'undated'}`}>#{e.id}</button>
            ))}
            {finding.earliestEventTime && <span className="ml-1 text-slate-500">{formatUtc(finding.earliestEventTime)}{finding.latestEventTime !== finding.earliestEventTime ? ` → ${formatUtc(finding.latestEventTime)}` : ''}</span>}
          </div>
        ) : (
          <p className="mt-2 text-xs text-amber-800">No supporting evidence linked.</p>
        )}
        {finding.missingEvidenceIds.length > 0 && <p className="mt-1 text-xs text-red-700">Linked record(s) no longer exist: {finding.missingEvidenceIds.join(', ')}</p>}
        <p className="mt-2 text-xs text-slate-500">
          By {finding.createdBy} · updated {formatRelative(finding.updatedAt)}
          {finding.reviewedBy && <> · {finding.reviewStatus === 'REJECTED' ? 'rejected' : 'reviewed'} by {finding.reviewedBy}{finding.reviewComment ? ` — “${finding.reviewComment}”` : ''}</>}
        </p>
      </li>
    );
  };

  return (
    <div className="space-y-4">
      <Tabs label="Workspace sections" active={section} onChange={setSection} tabs={[
        { id: 'narrative', label: 'Narrative', icon: <FileText className="h-4 w-4" />, count: data.narrative.length },
        { id: 'findings', label: 'All findings', count: data.findings.length },
        { id: 'bookmarks', label: 'Bookmarks', icon: <Bookmark className="h-4 w-4" />, count: data.bookmarks.length },
        { id: 'notes', label: 'Notes', icon: <NotebookPen className="h-4 w-4" />, count: data.notes.length }
      ]} />

      {section === 'narrative' && (
        <Card>
          <CardHeader title="Case narrative" description="Findings included in reports, in chronological order of their supporting evidence. Rejected findings are excluded."
            actions={canEdit && <Button size="sm" icon={<Plus className="h-4 w-4" />} onClick={() => { setEditingFinding(undefined); setFindingDialog(true); }}>Add finding</Button>} />
          {data.narrative.length === 0 ? (
            <EmptyState title="No findings in the narrative" description="Add findings from evidence records, the timeline or the assistant." />
          ) : (
            <ol className="divide-y divide-slate-100">{data.narrative.map((finding, index) => findingCard(finding, index + 1))}</ol>
          )}
        </Card>
      )}

      {section === 'findings' && (
        <Card>
          <CardHeader title="Findings" description="Every finding states its basis and links supporting records."
            actions={canEdit && <Button size="sm" icon={<Plus className="h-4 w-4" />} onClick={() => { setEditingFinding(undefined); setFindingDialog(true); }}>Add finding</Button>} />
          {data.findings.some((f) => f.aiGenerated && f.reviewStatus !== 'REVIEWED') && (
            <div className="px-5 pt-4"><Alert tone="warning">AI-assisted findings must be reviewed by a reviewer or administrator other than their author.</Alert></div>
          )}
          {data.findings.length === 0 ? <EmptyState title="No findings yet" /> : <ul className="divide-y divide-slate-100">{data.findings.map((f) => findingCard(f))}</ul>}
        </Card>
      )}

      {section === 'bookmarks' && (
        <Card>
          <CardHeader title="Bookmarked evidence" description="Records marked for attention. Bookmarked records are included as key events in reports." />
          {data.bookmarks.length === 0 ? (
            <EmptyState icon={<Bookmark className="h-6 w-6" />} title="No bookmarks" description="Open any evidence record and choose Bookmark." />
          ) : (
            <ul className="divide-y divide-slate-100">
              {data.bookmarks.map((bookmark) => (
                <li key={bookmark.id} className="flex flex-wrap items-start gap-3 px-5 py-3">
                  <button type="button" onClick={() => setEvidenceId(bookmark.evidenceId)} className="min-w-0 flex-1 text-left">
                    <p className="text-sm text-slate-900"><span className="mono text-brand-700">#{bookmark.evidenceId}</span> {humanize(bookmark.evidence.artifactType)} · {bookmark.evidence.eventTime ? formatUtc(bookmark.evidence.eventTime) : 'Undated'}</p>
                    <p className="line-clamp-2 text-sm text-slate-600">{bookmark.evidence.content ?? bookmark.evidence.title ?? '—'}</p>
                    <p className="text-xs text-slate-500">Bookmarked by {bookmark.createdBy} {formatRelative(bookmark.createdAt)}</p>
                  </button>
                  {canEdit && <IconButton label="Remove bookmark" onClick={() => unbookmark.mutate(bookmark.id)}><Trash2 className="h-4 w-4" /></IconButton>}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {section === 'notes' && (
        <Card>
          <CardHeader title="Investigation notes" description="Working notes for the case team. Notes are not included in reports."
            actions={canEdit && <Button size="sm" icon={<Plus className="h-4 w-4" />} onClick={() => setNoteDialog({})}>New note</Button>} />
          {data.notes.length === 0 ? (
            <EmptyState icon={<NotebookPen className="h-6 w-6" />} title="No notes" />
          ) : (
            <ul className="divide-y divide-slate-100">
              {data.notes.map((note) => (
                <li key={note.id} className="px-5 py-3">
                  <div className="flex items-start gap-2">
                    <p className="flex-1 text-sm font-semibold text-slate-900">{note.title}</p>
                    {canEdit && (
                      <>
                        <IconButton label="Edit note" onClick={() => setNoteDialog({ note })}><Pencil className="h-4 w-4" /></IconButton>
                        <IconButton label="Delete note" className="hover:text-red-600" onClick={() => setDeleting({ kind: 'note', id: note.id, title: note.title })}><Trash2 className="h-4 w-4" /></IconButton>
                      </>
                    )}
                  </div>
                  <p className="mt-1 whitespace-pre-wrap text-sm text-slate-700">{note.body}</p>
                  <p className="mt-1 text-xs text-slate-500">{note.updatedBy ?? note.createdBy} · {formatRelative(note.updatedAt)}</p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      <FindingDialog open={findingDialog} onClose={() => setFindingDialog(false)} caseId={caseId} finding={editingFinding} />
      <ReviewDialog caseId={caseId} target={reviewing} onClose={() => setReviewing(null)} onDone={invalidate} />
      <NoteDialog caseId={caseId} state={noteDialog} onClose={() => setNoteDialog(null)} onDone={invalidate} />
      <ConfirmDialog open={deleting !== null} title={`Delete ${deleting?.kind}?`} confirmLabel="Delete" loading={remove.isPending}
        message={<>Delete “{deleting?.title}”? The deletion is recorded in the audit log.</>}
        onConfirm={() => deleting && remove.mutate(deleting)} onCancel={() => setDeleting(null)} />
      <EvidenceDrawer evidenceId={evidenceId} onClose={() => setEvidenceId(null)} onSelect={setEvidenceId} />
    </div>
  );
}

function ReviewDialog({ caseId, target, onClose, onDone }: { caseId: number; target: { finding: Finding; decision: 'REVIEWED' | 'REJECTED' } | null; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const review = useMutation({
    mutationFn: () => reviewFinding(caseId, target!.finding.id, target!.decision, comment.trim() || undefined),
    onSuccess: () => { toast.success(target?.decision === 'REVIEWED' ? 'Finding approved.' : 'Finding rejected.'); setComment(''); onDone(); onClose(); },
    onError: (err) => setError(friendlyError(err))
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    if (target?.decision === 'REJECTED' && !comment.trim()) {
      setError('Explain why the finding is rejected.');
      return;
    }
    review.mutate();
  }
  return (
    <Dialog open={target !== null} onClose={onClose} title={target?.decision === 'REVIEWED' ? 'Approve finding' : 'Reject finding'} description={target?.finding.title}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="review-form" variant={target?.decision === 'REJECTED' ? 'danger' : 'primary'} loading={review.isPending}>{target?.decision === 'REVIEWED' ? 'Approve' : 'Reject'}</Button></>}>
      <form id="review-form" onSubmit={submit} className="space-y-3">
        <p className="text-sm text-slate-600">Confirm that the cited records support the finding as written{target?.finding.aiGenerated ? ', including the AI-assisted wording' : ''}.</p>
        <Field label={target?.decision === 'REJECTED' ? 'Reason (required)' : 'Comment (optional)'} error={error || undefined}>
          {(props) => <Textarea {...props} rows={3} maxLength={1000} value={comment} onChange={(event) => setComment(event.target.value)} />}
        </Field>
      </form>
    </Dialog>
  );
}

function NoteDialog({ caseId, state, onClose, onDone }: { caseId: number; state: { note?: Note } | null; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loadedFor, setLoadedFor] = useState<number | 'new' | null>(null);
  const key = state ? state.note?.id ?? 'new' : null;
  if (key !== loadedFor) {
    setLoadedFor(key);
    setTitle(state?.note?.title ?? '');
    setBody(state?.note?.body ?? '');
    setErrors({});
  }
  const save = useMutation({
    mutationFn: () => (state?.note ? updateNote(caseId, state.note.id, { title: title.trim(), body: body.trim() }) : createNote(caseId, { title: title.trim(), body: body.trim() })),
    onSuccess: () => { toast.success('Note saved.'); onDone(); onClose(); },
    onError: (error) => setErrors({ form: friendlyError(error) })
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = 'Enter a title.';
    if (!body.trim()) next.body = 'Enter the note text.';
    setErrors(next);
    if (!Object.keys(next).length) save.mutate();
  }
  return (
    <Dialog open={state !== null} onClose={onClose} title={state?.note ? 'Edit note' : 'New note'}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="note-form" loading={save.isPending}>Save note</Button></>}>
      <form id="note-form" onSubmit={submit} className="space-y-3" noValidate>
        {errors.form && <Alert tone="danger">{errors.form}</Alert>}
        <Field label="Title" required error={errors.title}>{(props) => <Input {...props} maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} />}</Field>
        <Field label="Note" required error={errors.body}>{(props) => <Textarea {...props} rows={6} value={body} onChange={(event) => setBody(event.target.value)} />}</Field>
      </form>
    </Dialog>
  );
}
