import { useForm } from 'react-hook-form';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import type { CasePriority, CaseRequest, CaseStatus } from '../../types/cases';

const statuses: CaseStatus[] = ['OPEN', 'ACTIVE', 'ON_HOLD', 'CLOSED', 'ARCHIVED'];
const priorities: CasePriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export function CaseForm({
  initialValues,
  isSubmitting,
  submitLabel,
  onSubmit,
  disableLeadInvestigator
}: {
  initialValues?: Partial<CaseRequest>;
  isSubmitting: boolean;
  submitLabel: string;
  onSubmit: (payload: CaseRequest) => void | Promise<void>;
  disableLeadInvestigator?: boolean;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors }
  } = useForm<CaseRequest>({
    defaultValues: {
      caseNumber: initialValues?.caseNumber ?? '',
      title: initialValues?.title ?? '',
      description: initialValues?.description ?? '',
      status: initialValues?.status ?? 'OPEN',
      priority: initialValues?.priority ?? 'MEDIUM',
      leadInvestigator: initialValues?.leadInvestigator ?? '',
      category: initialValues?.category ?? '',
      caseType: initialValues?.caseType ?? '',
      location: initialValues?.location ?? '',
      notes: initialValues?.notes ?? ''
    }
  });

  return (
    <form className="grid gap-4 md:grid-cols-2" onSubmit={handleSubmit(onSubmit)}>
      <div>
        <label className="mb-2 block text-sm text-slate-400">Case Number</label>
        <Input {...register('caseNumber', { required: 'Case number is required', maxLength: 80 })} placeholder="MC-2026-101" />
        {errors.caseNumber && <p className="mt-1 text-xs text-rose-300">{errors.caseNumber.message}</p>}
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Title</label>
        <Input {...register('title', { required: 'Title is required', maxLength: 180 })} placeholder="Case title" />
        {errors.title && <p className="mt-1 text-xs text-rose-300">{errors.title.message}</p>}
      </div>

      <div className="md:col-span-2">
        <label className="mb-2 block text-sm text-slate-400">Description</label>
        <textarea
          className="w-full rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyanline focus:ring-2 focus:ring-cyanline/20"
          rows={4}
          {...register('description', { required: 'Description is required', maxLength: 2000 })}
          placeholder="Investigation scope, context, and requested forensic objectives"
        />
        {errors.description && <p className="mt-1 text-xs text-rose-300">{errors.description.message}</p>}
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Status</label>
        <select className="w-full rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100" {...register('status')}>
          {statuses.map((status) => (
            <option key={status} value={status}>{status}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Priority</label>
        <select className="w-full rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100" {...register('priority')}>
          {priorities.map((priority) => (
            <option key={priority} value={priority}>{priority}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Lead Investigator</label>
        <Input {...register('leadInvestigator', { required: 'Lead investigator is required', maxLength: 120 })} readOnly={disableLeadInvestigator} />
        {errors.leadInvestigator && <p className="mt-1 text-xs text-rose-300">{errors.leadInvestigator.message}</p>}
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Category</label>
        <Input {...register('category', { required: 'Category is required', maxLength: 120 })} placeholder="Financial Crime" />
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Case Type</label>
        <Input {...register('caseType', { required: 'Case type is required', maxLength: 120 })} placeholder="Mobile Forensics" />
      </div>

      <div>
        <label className="mb-2 block text-sm text-slate-400">Location</label>
        <Input {...register('location', { maxLength: 255 })} placeholder="Zone-3" />
      </div>

      <div className="md:col-span-2">
        <label className="mb-2 block text-sm text-slate-400">Notes</label>
        <textarea
          className="w-full rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-cyanline focus:ring-2 focus:ring-cyanline/20"
          rows={3}
          {...register('notes', { maxLength: 4000 })}
          placeholder="Operational notes"
        />
      </div>

      <div className="md:col-span-2 flex justify-end">
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Saving...' : submitLabel}</Button>
      </div>
    </form>
  );
}
