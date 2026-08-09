export type CaseStatus = 'OPEN' | 'ACTIVE' | 'ON_HOLD' | 'CLOSED' | 'ARCHIVED';
export type CasePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

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

export interface CasePageResponse {
  items: CaseListItem[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first: boolean;
  last: boolean;
}

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

export interface CaseInvestigator {
  name: string;
  role: string;
  assignmentType: string;
}

export interface CaseDevice {
  id: number;
  deviceName: string;
  platform: string;
  acquisitionStatus: string;
}

export interface CaseEvidence {
  id: number;
  artifactType: string;
  fileName: string;
  hashSha256: string;
  deviceName: string;
  createdAt: string;
}

export interface CaseReport {
  id: number;
  reportNumber: string;
  status: string;
  generatedAt: string;
}

export interface CaseActivity {
  id: number;
  actor: string;
  action: string;
  description: string;
  timestamp: string;
}

export interface CaseDetailResponse {
  overview: CaseResponse;
  investigators: CaseInvestigator[];
  devices: CaseDevice[];
  evidence: CaseEvidence[];
  timeline: CaseActivity[];
  reports: CaseReport[];
  notes: string;
  activity: CaseActivity[];
}
