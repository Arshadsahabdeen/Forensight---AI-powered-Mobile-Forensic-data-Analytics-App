// Types mirroring the backend DTOs (backend/src/main/java/com/forensics/platform/dto).

export type CaseStatus = 'OPEN' | 'ACTIVE' | 'ON_HOLD' | 'CLOSED' | 'ARCHIVED';
export type CasePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type DeviceStatus =
  | 'SEIZED'
  | 'ACQUIRED'
  | 'WAITING_FOR_EXTRACTION'
  | 'EXTRACTION_COMPLETED'
  | 'UNDER_ANALYSIS'
  | 'REPORT_GENERATED'
  | 'ARCHIVED';
export type AcquisitionMethod =
  | 'LOGICAL_EXTRACTION'
  | 'FILE_SYSTEM_EXTRACTION'
  | 'PHYSICAL_EXTRACTION'
  | 'CHIP_OFF'
  | 'JTAG'
  | 'MANUAL_COLLECTION'
  | 'IMPORTED_UFDR'
  | 'IMPORTED_AXIOM'
  | 'OTHER';
export type TimestampStatus = 'EXACT' | 'ASSUMED_UTC' | 'DATE_ONLY' | 'MISSING' | 'UNPARSEABLE' | 'LEGACY_IMPORT';
export type EvidenceCategory = 'COMMUNICATION' | 'LOCATION' | 'CONTACT' | 'MEDIA' | 'BROWSER' | 'ACCOUNT' | 'OTHER';

export interface Page<T> {
  items: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first?: boolean;
  last?: boolean;
}

// ---------- cases
export interface CaseRequest {
  caseNumber: string;
  title: string;
  description: string;
  status: CaseStatus;
  priority: CasePriority;
  leadInvestigator: string;
  category: string;
  caseType: string;
  location?: string;
  notes?: string;
}

export interface CaseResponse extends CaseRequest {
  id: number;
  createdAt: string;
  updatedAt: string;
}

export interface CaseListItem {
  id: number;
  caseNumber: string;
  title: string;
  status: CaseStatus;
  priority: CasePriority;
  leadInvestigator: string;
  category: string;
  createdAt: string;
  updatedAt: string;
}

export interface CaseDetail {
  overview: CaseResponse;
  investigators: { name: string; role: string; assignmentType: string }[];
  devices: { id: number; deviceName: string; operatingSystem: string | null; acquisitionStatus: DeviceStatus; imei: string | null; evidenceCount: number }[];
  evidenceSummary: {
    totalRecords: number;
    evidenceFiles: number;
    recordsByType: Record<string, number>;
    filesVerified: number;
    filesWithIntegrityIssues: number;
    filesFailedProcessing: number;
  };
  findingSummary: { total: number; needsReview: number; reviewed: number; aiGenerated: number };
  reports: { id: number; reportNumber: string; reportTitle: string | null; status: string; generatedAt: string | null; hasSnapshot: boolean }[];
  activity: ActivityItem[];
  canEdit: boolean;
  canDelete: boolean;
}

export interface ActivityItem {
  id: number;
  actor: string | null;
  action: string;
  description: string | null;
  timestamp: string;
  success: boolean;
  caseId?: number | null;
}

// ---------- devices
export interface DeviceRequest {
  caseId: number;
  manufacturer: string;
  brand?: string;
  model: string;
  deviceName: string;
  imei?: string;
  serialNumber?: string;
  operatingSystem: string;
  osVersion?: string;
  deviceType?: string;
  ownerName?: string;
  ownerContact?: string;
  investigator?: string;
  acquisitionMethod: AcquisitionMethod;
  acquisitionStatus: DeviceStatus;
  seizedDate?: string | null;
  receivedDate?: string | null;
  notes?: string;
}

export interface Device {
  id: number;
  caseId: number | null;
  caseNumber: string | null;
  manufacturer: string | null;
  brand: string | null;
  model: string | null;
  deviceName: string | null;
  imei: string | null;
  serialNumber: string | null;
  operatingSystem: string | null;
  osVersion: string | null;
  deviceType: string | null;
  ownerName: string | null;
  ownerContact: string | null;
  investigator: string | null;
  acquisitionMethod: AcquisitionMethod | null;
  acquisitionStatus: DeviceStatus | null;
  seizedDate: string | null;
  receivedDate: string | null;
  notes: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeviceListItem {
  id: number;
  deviceName: string | null;
  manufacturer: string | null;
  model: string | null;
  imei: string | null;
  operatingSystem: string | null;
  acquisitionStatus: DeviceStatus | null;
  investigator: string | null;
  caseId: number | null;
  caseNumber: string | null;
  updatedAt: string;
}

export interface DeviceDetail {
  device: Device;
  evidenceCount: number;
  reportCount: number;
  timelineCount: number;
  evidenceFileCount: number;
  canEdit: boolean;
  canDelete: boolean;
}

// ---------- evidence
export interface Evidence {
  id: number;
  artifactType: string | null;
  category: EvidenceCategory;
  fileName: string | null;
  hashSha256: string | null;
  hashNote: string | null;
  title: string | null;
  content: string | null;
  source: string | null;
  target: string | null;
  phoneNumber: string | null;
  contactName: string | null;
  location: string | null;
  latitude: number | null;
  longitude: number | null;
  eventTime: string | null;
  originalTimestamp: string | null;
  originalTimezone: string | null;
  timestampStatus: TimestampStatus;
  createdAt: string | null;
  deviceId: number | null;
  deviceName: string | null;
  caseId: number | null;
  caseNumber: string | null;
  sourceFileId: number | null;
  sourceRecordRef: string | null;
  recordHash: string | null;
}

export interface EvidenceDetail {
  evidence: Evidence;
  normalized: { id: number; category: EvidenceCategory; sourceTool: string; title: string | null; content: string | null; metadata: Record<string, unknown> } | null;
  bookmarkId: number | null;
  findings: { id: number; title: string; basis: string; reviewStatus: string }[];
}

export interface RelatedEvidence {
  id: number;
  relationshipType: string;
  relationship: string;
  basis: 'VERIFIED_RECORD' | 'INFERRED';
  reason: string;
  evidence: Evidence;
}

export interface EvidenceQuery {
  page: number;
  size: number;
  sort: 'eventTime' | 'createdAt' | 'id' | 'artifactType';
  direction: 'asc' | 'desc';
  search?: string;
  artifactType?: string;
  category?: string;
  deviceId?: number;
  caseId?: number;
  fileId?: number;
  startTime?: string;
  endTime?: string;
  entity?: string;
  timestampStatus?: string;
}

export interface FilterOptions {
  artifactTypes: string[];
  categories: string[];
  timestampStatuses: string[];
}

// ---------- files & processing
export type ProcessingStatus = 'RECEIVED' | 'PROCESSING' | 'PROCESSED' | 'FAILED';
export type IntegrityStatus = 'NOT_VERIFIED' | 'VERIFIED' | 'MISMATCH' | 'MISSING';
export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface ProcessingJob {
  id: number;
  jobType: string;
  status: JobStatus;
  progress: number;
  stage: string | null;
  attempts: number;
  errorMessage: string | null;
  caseId: number;
  caseNumber: string;
  deviceId: number | null;
  deviceName: string | null;
  evidenceFileId: number | null;
  fileName: string | null;
  createdBy: string;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  canRetry: boolean;
}

export interface JobPage extends Page<ProcessingJob> {
  queued: number;
  running: number;
  failed: number;
}

export interface JobDetail {
  job: ProcessingJob;
  logs: { id: number; loggedAt: string; level: string; message: string }[];
}

export interface EvidenceFile {
  id: number;
  caseId: number;
  caseNumber: string;
  deviceId: number | null;
  deviceName: string | null;
  originalFileName: string;
  contentType: string | null;
  sourceFormat: 'XML' | 'UFDR' | 'ZIP';
  sizeBytes: number;
  sha256: string;
  importedBy: string;
  importedAt: string;
  processingStatus: ProcessingStatus;
  integrityStatus: IntegrityStatus;
  lastVerifiedAt: string | null;
  lastVerifiedBy: string | null;
  recordsExtracted: number;
  duplicatesSkipped: number;
  timestampIssues: number;
  latestJob: ProcessingJob | null;
}

export interface UploadResponse {
  file: EvidenceFile;
  job: ProcessingJob;
  message: string;
}

export interface Verification {
  fileId: number;
  status: IntegrityStatus;
  recordedSha256: string;
  computedSha256: string | null;
  verifiedAt: string;
  verifiedBy: string;
  message: string;
}

export interface CustodyEvent {
  id: number;
  timestamp: string;
  actor: string | null;
  action: string;
  description: string | null;
  success: boolean;
}

// ---------- timeline
export interface TimelineFlag {
  code: string;
  severity: 'INFO' | 'WARNING';
  message: string;
  relatedEvidenceIds: number[];
}

export interface TimelineEvent {
  evidenceId: number;
  artifactType: string | null;
  category: EvidenceCategory;
  title: string | null;
  summary: string | null;
  source: string | null;
  target: string | null;
  phoneNumber: string | null;
  contactName: string | null;
  location: string | null;
  latitude: number | null;
  longitude: number | null;
  eventTime: string | null;
  originalTimestamp: string | null;
  originalTimezone: string | null;
  timestampStatus: TimestampStatus;
  deviceId: number | null;
  deviceName: string | null;
  caseId: number | null;
  sourceFileId: number | null;
  sourceRecordRef: string | null;
  flags: TimelineFlag[];
}

export interface TimelineResponse {
  caseId: number;
  deviceId: number | null;
  page: number;
  size: number;
  totalDated: number;
  totalPages: number;
  totalUndated: number;
  firstEventTime: string | null;
  lastEventTime: string | null;
  timestampStatusCounts: Partial<Record<TimestampStatus, number>>;
  flaggedEvents: number;
  events: TimelineEvent[];
  undated: TimelineEvent[];
  timezoneNote: string;
}

// ---------- correlation
export type EntityType = 'PERSON' | 'PHONE' | 'ACCOUNT' | 'LOCATION' | 'DEVICE' | 'EVENT';

export interface GraphEntity {
  id: string;
  type: EntityType;
  label: string;
  variants: string[];
  evidenceCount: number;
  deviceIds: number[];
  firstSeen: string | null;
  lastSeen: string | null;
  note: string | null;
}

export interface GraphRelationship {
  id: string;
  source: string;
  target: string;
  type: string;
  basis: 'VERIFIED_RECORD' | 'INFERRED';
  rule: string;
  explanation: string;
  recordCount: number;
  evidenceIds: number[];
  evidenceTruncated: boolean;
  firstSeen: string | null;
  lastSeen: string | null;
}

export interface CorrelationGraph {
  caseId: number;
  deviceId: number | null;
  generatedAt: string;
  evidenceConsidered: number;
  entities: GraphEntity[];
  relationships: GraphRelationship[];
  rules: { code: string; basis: string; description: string }[];
  disclaimer: string;
  truncated: boolean;
}

// ---------- workspace
export type FindingBasis = 'VERIFIED_FACT' | 'INFERENCE' | 'AI_SUGGESTED';
export type Significance = 'LOW' | 'MEDIUM' | 'HIGH';
export type ReviewStatus = 'DRAFT' | 'NEEDS_REVIEW' | 'REVIEWED' | 'REJECTED';

export interface Bookmark {
  id: number;
  caseId: number;
  evidenceId: number;
  label: string | null;
  note: string | null;
  createdBy: string;
  createdAt: string;
  evidence: Evidence;
}

export interface Note {
  id: number;
  caseId: number;
  title: string;
  body: string;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
}

export interface FindingRequest {
  title: string;
  description: string;
  basis: FindingBasis;
  significance: Significance;
  evidenceIds: number[];
  includeInReport?: boolean;
  narrativeOrder?: number | null;
  aiGenerated?: boolean;
  aiEngine?: string | null;
}

export interface Finding {
  id: number;
  caseId: number;
  title: string;
  description: string;
  basis: FindingBasis;
  significance: Significance;
  reviewStatus: ReviewStatus;
  aiGenerated: boolean;
  aiEngine: string | null;
  includeInReport: boolean;
  narrativeOrder: number | null;
  evidence: Evidence[];
  missingEvidenceIds: number[];
  earliestEventTime: string | null;
  latestEventTime: string | null;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
}

export interface Workspace {
  bookmarks: Bookmark[];
  notes: Note[];
  findings: Finding[];
  narrative: Finding[];
}

// ---------- assistant
export type AssistantTask = 'SEARCH' | 'SUMMARIZE' | 'ENTITIES' | 'ASK';

export interface AssistantResponse {
  task: AssistantTask;
  engine: string;
  model: string | null;
  status: 'COMPLETED' | 'FALLBACK' | 'NO_EVIDENCE';
  answer: string;
  answerIsAiGenerated: boolean;
  statements: { text: string; kind: 'RECORD_FACT' | 'AGGREGATE' | 'INFERENCE'; evidenceIds: number[] }[];
  citations: { evidenceId: number; reason: string; snippet: string | null; artifactType: string | null; eventTime: string | null; deviceName: string | null }[];
  entities: { type: string; value: string; evidenceIds: number[] }[];
  limitations: string[];
  disclaimer: string;
  evidenceConsidered: number;
  evidenceAvailable: number;
  droppedUngroundedItems: number;
  generatedAt: string;
}

export interface AssistantStatus {
  reachable: boolean;
  engine: string;
  provider: string | null;
  model: string | null;
  llmConfigured: boolean;
  externalProcessingAllowed: boolean;
  message: string;
  serviceUrl: string;
}

// ---------- reports
export interface ReportSummary {
  id: number;
  reportNumber: string | null;
  title: string | null;
  reportType: string | null;
  caseId: number | null;
  caseNumber: string | null;
  status: string | null;
  generatedAt: string | null;
  generatedBy: string | null;
  preparedBy: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  contentSha256: string | null;
  hasSnapshot: boolean;
  findingsCount: number;
  aiFindingsCount: number;
  unreviewedAiFindings: number;
}

export interface EvidenceRef {
  evidenceId: number;
  artifactType: string | null;
  eventTime: string | null;
  originalTimestamp: string | null;
  originalTimezone: string | null;
  timestampStatus: string | null;
  summary: string | null;
  participants: string;
  deviceName: string | null;
  sourceFileId: number | null;
  sourceRecordRef: string | null;
}

export interface ReportSnapshot {
  schemaVersion: string;
  reportNumber: string;
  title: string;
  generatedAt: string;
  generatedBy: string;
  preparedBy: string;
  caseDetails: {
    id: number;
    caseNumber: string;
    title: string;
    description: string;
    status: string;
    priority: string;
    category: string;
    caseType: string;
    location: string | null;
    leadInvestigator: string;
    createdAt: string;
    updatedAt: string;
  };
  devices: {
    id: number;
    deviceName: string | null;
    manufacturer: string | null;
    model: string | null;
    imei: string | null;
    serialNumber: string | null;
    operatingSystem: string | null;
    osVersion: string | null;
    acquisitionMethod: string | null;
    acquisitionStatus: string | null;
    seizedDate: string | null;
    receivedDate: string | null;
    evidenceCount: number;
  }[];
  evidenceSources: {
    id: number;
    originalFileName: string;
    sourceFormat: string;
    sizeBytes: number;
    sha256: string;
    importedBy: string;
    importedAt: string;
    processingStatus: string;
    integrityStatus: string;
    lastVerifiedAt: string | null;
    lastVerifiedBy: string | null;
    recordsExtracted: number;
    duplicatesSkipped: number;
    timestampIssues: number;
    deviceName: string | null;
  }[];
  evidenceOverview: {
    totalRecords: number;
    recordsByType: Record<string, number>;
    recordsByTimestampStatus: Record<string, number>;
    firstEventTime: string | null;
    lastEventTime: string | null;
    undatedRecords: number;
    recordsWithoutSourceFile: number;
  };
  findings: {
    id: number;
    title: string;
    description: string;
    basis: FindingBasis;
    significance: Significance;
    reviewStatus: ReviewStatus;
    aiGenerated: boolean;
    aiEngine: string | null;
    createdBy: string;
    reviewedBy: string | null;
    reviewedAt: string | null;
    reviewComment: string | null;
    earliestEventTime: string | null;
    evidence: EvidenceRef[];
  }[];
  keyEvents: { eventTime: string | null; timestampStatus: string; flags: string; evidence: EvidenceRef }[];
  fullTimelineIncluded: boolean;
  methodology: string[];
  limitations: string[];
  auditTrail: { id: number; timestamp: string; actor: string | null; action: string; description: string | null; success: boolean }[];
  auditChain: { status: string; recordsChecked: number; legacyUnchainedRecords: number; firstBrokenRecordId: number | null; verifiedAt: string };
  disclaimer: string;
}

export interface ReportDetail {
  summary: ReportSummary;
  snapshot: ReportSnapshot | null;
  snapshotVerified: boolean | null;
  legacy: {
    aiSummary: string | null;
    timelineSummary: string | null;
    extractedKeywords: string | null;
    namedEntities: string | null;
    suspiciousFindings: string | null;
    riskLevel: string | null;
    notice: string;
  } | null;
}

// ---------- dashboard
export interface DashboardData {
  scope: 'ALL_CASES' | 'ASSIGNED_CASES';
  generatedAt: string;
  cases: { total: number; active: number; openHighPriority: number; byStatus: Metric[]; byPriority: Metric[] };
  evidence: { devices: number; records: number; files: number; filesByIntegrity: Record<IntegrityStatus, number>; recordsByType: Metric[] };
  processing: { queued: number; running: number; failed: number; recentJobs: ProcessingJob[] };
  review: { findingsNeedingReview: number; reportsTotal: number; reportsAwaitingReview: number };
  recentCases: { id: number; caseNumber: string; title: string; status: CaseStatus; priority: CasePriority; leadInvestigator: string; updatedAt: string }[];
  recentActivities: ActivityItem[];
}

export interface Metric {
  label: string;
  value: number;
}

// ---------- admin
export interface AdminUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  roles: ('ADMIN' | 'INVESTIGATOR' | 'REVIEWER')[];
  enabled: boolean;
  accountLocked: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEntry {
  id: number;
  userId: number | null;
  username: string | null;
  action: string;
  timestamp: string;
  description: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
  caseId: number | null;
  entityType: string | null;
  entityId: string | null;
  chained: boolean;
}

export interface ChainVerification {
  status: 'INTACT' | 'BROKEN';
  recordsChecked: number;
  legacyUnchainedRecords: number;
  firstBrokenRecordId: number | null;
  detail: string;
  verifiedAt: string;
}

export interface PlatformSettings {
  auditRetentionDays: number;
  sessionTimeoutMinutes: number;
  loginAlertsEnabled: boolean;
  maintenanceMode: boolean;
  defaultPageSize: number;
}

export interface SessionPolicy {
  sessionTimeoutMinutes: number;
  serverIdleTimeoutMinutes: number;
  accessTokenMinutes: number;
  maintenanceMode: boolean;
  defaultPageSize: number;
}

export interface DirectoryUser {
  username: string;
  fullName: string;
  roles: string[];
}
