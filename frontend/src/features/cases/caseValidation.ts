import type { CaseRequest } from '../../types/api';

/** Mirrors the backend's CaseRequest validation so users see problems before submitting. */
export function validateCase(values: CaseRequest): Partial<Record<keyof CaseRequest, string>> {
  const errors: Partial<Record<keyof CaseRequest, string>> = {};
  const caseNumber = values.caseNumber.trim();
  if (!caseNumber) errors.caseNumber = 'Case number is required.';
  else if (caseNumber.length > 80) errors.caseNumber = 'Use 80 characters or fewer.';
  else if (!/^[A-Za-z0-9][A-Za-z0-9._/-]*$/.test(caseNumber)) errors.caseNumber = "Use letters, numbers, '.', '_', '-' or '/' only.";
  if (!values.title.trim()) errors.title = 'Title is required.';
  else if (values.title.length > 180) errors.title = 'Use 180 characters or fewer.';
  if (!values.description.trim()) errors.description = 'Description is required.';
  else if (values.description.length > 2000) errors.description = 'Use 2000 characters or fewer.';
  if (!values.leadInvestigator.trim()) errors.leadInvestigator = 'Select the lead investigator.';
  if (!values.category.trim()) errors.category = 'Category is required.';
  if (!values.caseType.trim()) errors.caseType = 'Case type is required.';
  if ((values.location ?? '').length > 255) errors.location = 'Use 255 characters or fewer.';
  if ((values.notes ?? '').length > 4000) errors.notes = 'Use 4000 characters or fewer.';
  return errors;
}
