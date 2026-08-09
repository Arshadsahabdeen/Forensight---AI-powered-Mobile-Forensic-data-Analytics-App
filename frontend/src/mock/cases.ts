import { mockDevices } from './devices';
import { mockEvidence } from './evidence';
import { mockUsers } from './users';

export type MockCaseStatus = 'Open' | 'Active' | 'Closed' | 'Archived';
export type MockCasePriority = 'Low' | 'Medium' | 'High' | 'Critical';

export interface MockCase {
  id: number;
  caseNumber: string;
  title: string;
  description: string;
  status: MockCaseStatus;
  priority: MockCasePriority;
  leadInvestigator: string;
  createdDate: string;
  updatedDate: string;
  location: string;
  deviceCount: number;
  evidenceCount: number;
  reportStatus: 'Draft' | 'In Review' | 'Generated';
}

const caseTitles = [
  'Illegal Financial Fraud Investigation',
  'Mobile Theft Investigation',
  'Cyber Harassment Case',
  'Digital Fraud Analysis',
  'Drug Trafficking Investigation',
  'Corporate Data Leakage',
  'Identity Theft Case',
  'Online Scam Investigation',
  'Encrypted Device Analysis',
  'Missing Person Investigation',
  'SIM Swap Incident Investigation',
  'Cryptocurrency Laundering Inquiry',
  'Insider Threat Mobile Audit',
  'Social Media Blackmail Case',
  'Ransomware Device Containment',
  'Counterfeit App Distribution Case',
  'Darknet Communication Mapping',
  'Unauthorized Access Investigation',
  'Account Takeover Intelligence Case',
  'Digital Extortion Investigation'
] as const;

const baseCases = Array.from({ length: 20 }).map((_, index) => ({
  id: index + 1,
  caseNumber: `MC-2026-${String(index + 1).padStart(3, '0')}`,
  title: caseTitles[index],
  description: `Forensic examination for ${caseTitles[index].toLowerCase()} including mobile extraction, artifact validation, and reporting.`,
  status: (index % 4 === 0 ? 'Open' : index % 4 === 1 ? 'Active' : index % 4 === 2 ? 'Closed' : 'Archived') as MockCaseStatus,
  priority: (index % 4 === 0 ? 'Low' : index % 4 === 1 ? 'Medium' : index % 4 === 2 ? 'High' : 'Critical') as MockCasePriority,
  leadInvestigator: mockUsers[index % mockUsers.length].fullName,
  createdDate: `2026-05-${String((index % 28) + 1).padStart(2, '0')}T09:00:00Z`,
  updatedDate: `2026-07-${String((index % 14) + 1).padStart(2, '0')}T13:20:00Z`,
  location: `Forensic Lab Zone ${(index % 6) + 1}`,
  reportStatus: (index % 3 === 0 ? 'Draft' : index % 3 === 1 ? 'In Review' : 'Generated') as MockCase['reportStatus']
}));

export const mockCases: MockCase[] = baseCases.map((item) => ({
  ...item,
  deviceCount: mockDevices.filter((device) => device.caseId === item.id).length,
  evidenceCount: mockEvidence.filter((evidence) => evidence.caseId === item.id).length
}));
