import { api } from './api';
import type {
  AdminUser,
  AssistantResponse,
  AssistantStatus,
  AssistantTask,
  AuditEntry,
  Bookmark,
  CaseDetail,
  CaseListItem,
  CaseRequest,
  CaseResponse,
  ChainVerification,
  CorrelationGraph,
  CustodyEvent,
  DashboardData,
  Device,
  DeviceDetail,
  DeviceListItem,
  DeviceRequest,
  DirectoryUser,
  Evidence,
  EvidenceDetail,
  EvidenceFile,
  EvidenceQuery,
  FilterOptions,
  Finding,
  FindingRequest,
  JobDetail,
  JobPage,
  Note,
  Page,
  PlatformSettings,
  ProcessingJob,
  RelatedEvidence,
  ReportDetail,
  ReportSummary,
  SessionPolicy,
  TimelineResponse,
  UploadResponse,
  Verification,
  Workspace
} from '../types/api';

/** Drops empty values so the backend only receives filters that are set. */
export function compact<T extends object>(params: T): Record<string, string | number | boolean> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  ) as Record<string, string | number | boolean>;
}

// ---------- dashboard / directory
export const getDashboard = async () => (await api.get<DashboardData>('/dashboard')).data;
export const getInvestigators = async () => (await api.get<DirectoryUser[]>('/directory/investigators')).data;
export const getSessionPolicy = async () => (await api.get<SessionPolicy>('/platform/policy')).data;

// ---------- cases
export interface CaseQuery {
  page: number;
  size: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  search?: string;
  status?: string;
  priority?: string;
}
export const getCases = async (params: CaseQuery) => (await api.get<Page<CaseListItem>>('/cases', { params: compact(params) })).data;
export const getCase = async (id: number) => (await api.get<CaseDetail>(`/cases/${id}`)).data;
export const createCase = async (body: CaseRequest) => (await api.post<CaseResponse>('/cases', body)).data;
export const updateCase = async (id: number, body: CaseRequest) => (await api.put<CaseResponse>(`/cases/${id}`, body)).data;
export const deleteCase = async (id: number) => {
  await api.delete(`/cases/${id}`);
};

// ---------- devices
export interface DeviceQuery {
  page: number;
  size: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  search?: string;
  status?: string;
  operatingSystem?: string;
  caseId?: number;
}
export const getDevices = async (params: DeviceQuery) => (await api.get<Page<DeviceListItem>>('/devices', { params: compact(params) })).data;
export const getDevice = async (id: number) => (await api.get<DeviceDetail>(`/devices/${id}`)).data;
export const createDevice = async (body: DeviceRequest) => (await api.post<Device>('/devices', body)).data;
export const updateDevice = async (id: number, body: DeviceRequest) => (await api.put<Device>(`/devices/${id}`, body)).data;
export const deleteDevice = async (id: number) => {
  await api.delete(`/devices/${id}`);
};

// ---------- evidence
export const searchEvidence = async (params: EvidenceQuery) => (await api.get<Page<Evidence>>('/evidence', { params: compact(params) })).data;
export const getEvidenceDetail = async (id: number) => (await api.get<EvidenceDetail>(`/evidence/${id}`)).data;
export const getRelatedEvidence = async (id: number) => (await api.get<RelatedEvidence[]>(`/evidence/${id}/related`)).data;
export const getEvidenceFilters = async () => (await api.get<FilterOptions>('/evidence/filters')).data;

// ---------- evidence files & processing
export async function uploadEvidenceFile(caseId: number, file: File, deviceId?: number, onProgress?: (percent: number) => void) {
  const form = new FormData();
  form.append('file', file);
  const { data } = await api.post<UploadResponse>(`/cases/${caseId}/evidence-files`, form, {
    params: compact({ deviceId }),
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
    onUploadProgress: (event) => {
      if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100));
    }
  });
  return data;
}
export const getEvidenceFiles = async (caseId: number) => (await api.get<EvidenceFile[]>(`/cases/${caseId}/evidence-files`)).data;
export const verifyEvidenceFile = async (id: number) => (await api.post<Verification>(`/evidence-files/${id}/verify`)).data;
export const getCustody = async (id: number) => (await api.get<CustodyEvent[]>(`/evidence-files/${id}/custody`)).data;
export const getJobs = async (params: { caseId?: number; status?: string; page: number; size: number }) =>
  (await api.get<JobPage>('/processing/jobs', { params: compact(params) })).data;
export const getJob = async (id: number) => (await api.get<JobDetail>(`/processing/jobs/${id}`)).data;
export const retryJob = async (id: number) => (await api.post<ProcessingJob>(`/processing/jobs/${id}/retry`)).data;

// ---------- timeline / correlation
export interface TimelineQuery {
  caseId: number;
  deviceId?: number;
  entity?: string;
  types?: string;
  category?: string;
  from?: string;
  to?: string;
  flaggedOnly?: boolean;
  order?: 'asc' | 'desc';
  page: number;
  size: number;
}
export const getTimeline = async (params: TimelineQuery) => (await api.get<TimelineResponse>('/timeline', { params: compact(params) })).data;
export const getCorrelations = async (caseId: number, params: { deviceId?: number; includeEvents?: boolean; windowMinutes?: number }) =>
  (await api.get<CorrelationGraph>(`/cases/${caseId}/correlations`, { params: compact(params) })).data;

// ---------- workspace
export const getWorkspace = async (caseId: number) => (await api.get<Workspace>(`/cases/${caseId}/workspace`)).data;
export const addBookmark = async (caseId: number, evidenceId: number, label?: string) =>
  (await api.post<Bookmark>(`/cases/${caseId}/workspace/bookmarks`, { evidenceId, label })).data;
export const removeBookmark = async (caseId: number, bookmarkId: number) => {
  await api.delete(`/cases/${caseId}/workspace/bookmarks/${bookmarkId}`);
};
export const createNote = async (caseId: number, body: { title: string; body: string }) =>
  (await api.post<Note>(`/cases/${caseId}/workspace/notes`, body)).data;
export const updateNote = async (caseId: number, id: number, body: { title: string; body: string }) =>
  (await api.put<Note>(`/cases/${caseId}/workspace/notes/${id}`, body)).data;
export const deleteNote = async (caseId: number, id: number) => {
  await api.delete(`/cases/${caseId}/workspace/notes/${id}`);
};
export const createFinding = async (caseId: number, body: FindingRequest) =>
  (await api.post<Finding>(`/cases/${caseId}/workspace/findings`, body)).data;
export const updateFinding = async (caseId: number, id: number, body: FindingRequest) =>
  (await api.put<Finding>(`/cases/${caseId}/workspace/findings/${id}`, body)).data;
export const submitFinding = async (caseId: number, id: number) =>
  (await api.post<Finding>(`/cases/${caseId}/workspace/findings/${id}/submit`)).data;
export const reviewFinding = async (caseId: number, id: number, decision: 'REVIEWED' | 'REJECTED', comment?: string) =>
  (await api.post<Finding>(`/cases/${caseId}/workspace/findings/${id}/review`, { decision, comment })).data;
export const deleteFinding = async (caseId: number, id: number) => {
  await api.delete(`/cases/${caseId}/workspace/findings/${id}`);
};

// ---------- assistant
export const askAssistant = async (caseId: number, body: { task: AssistantTask; question?: string; deviceId?: number; maxRecords?: number }) =>
  (await api.post<AssistantResponse>(`/cases/${caseId}/assistant`, body)).data;
export const getAssistantStatus = async () => (await api.get<AssistantStatus>('/assistant/status')).data;

// ---------- reports
export const generateReport = async (caseId: number, body: { title?: string; includeFullTimeline?: boolean; includeAuditTrail?: boolean }) =>
  (await api.post<ReportSummary>(`/cases/${caseId}/reports`, body)).data;
export const getReports = async (caseId?: number) => (await api.get<ReportSummary[]>('/reports', { params: compact({ caseId }) })).data;
export const getReport = async (id: number) => (await api.get<ReportDetail>(`/reports/${id}`)).data;
export const reviewReport = async (id: number, comment?: string) => (await api.post<ReportSummary>(`/reports/${id}/review`, { comment })).data;
export const deleteReport = async (id: number) => {
  await api.delete(`/reports/${id}`);
};

// ---------- administration
export const getUsers = async () => (await api.get<AdminUser[]>('/users')).data;
export const createUser = async (body: { username: string; fullName: string; email: string; password: string; role: string }) =>
  (await api.post<AdminUser>('/users', body)).data;
export const resetPassword = async (id: number, newPassword: string) => (await api.post<AdminUser>(`/users/${id}/password`, { newPassword })).data;
export const updateUserStatus = async (id: number, enabled: boolean, accountLocked: boolean) =>
  (await api.patch<AdminUser>(`/users/${id}/status`, { enabled, accountLocked })).data;
export const updateUserRole = async (id: number, role: string) => (await api.patch<AdminUser>(`/users/${id}/role`, { role })).data;
export const getSettings = async () => (await api.get<PlatformSettings>('/users/settings')).data;
export const updateSettings = async (body: PlatformSettings) => (await api.patch<PlatformSettings>('/users/settings', body)).data;

export interface AuditQuery {
  page: number;
  size: number;
  action?: string;
  username?: string;
  success?: boolean;
  caseId?: number;
  from?: string;
  to?: string;
  search?: string;
}
export const getAuditLogs = async (params: AuditQuery) => (await api.get<Page<AuditEntry>>('/audit', { params: compact(params) })).data;
export const getAuditActions = async () => (await api.get<string[]>('/audit/actions')).data;
export const verifyAuditChain = async () => (await api.post<ChainVerification>('/audit/verify')).data;
