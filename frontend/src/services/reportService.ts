import { api } from "./api";

export interface StoredReport {
  id: number;

  reportNumber: string | null;

  reportTitle: string | null;

  reportType: string | null;

  version: string | null;

  status: string | null;

  generatedAt: string | null;

  preparedBy: string | null;

  reviewedBy: string | null;

  approvedBy: string | null;

  generatedBy: string | null;

  deviceId: number | null;

  notesHtml: string | null;

  aiSummary: string | null;

  timelineSummary: string | null;

  extractedKeywords: string | null;

  namedEntities: string | null;

  suspiciousFindings: string | null;

  riskLevel: string | null;

  createdAt: string | null;

  updatedAt: string | null;

  investigationCase: {
    id: number;
    caseNumber?: string;
    title?: string;
  } | null;
}

interface StoredReportsResponse {
  content?: StoredReport[];
  data?: StoredReport[];
}

export interface InvestigationReport {
  deviceId: number;

  device: {
    id: number;
    deviceName: string | null;
    manufacturer: string | null;
    brand: string | null;
    model: string | null;
    imei: string | null;
    operatingSystem: string | null;
  };

  evidenceSummary: {
    totalEvidence: number;
    byArtifactType: Record<string, number>;
  };

  timeline: Array<{
    id: number;
    artifactType: string;
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
    deviceId: number | null;
  }>;

  relationshipGraph: unknown;

  aiAnalysis: {
    status: string;
    summary: string;
    entities: {
      phoneNumbers: string[];
      contacts: string[];
      locations: string[];
    };
    keywords: string[];
    statistics: {
      totalEvidence: number;
      communications: number;
      locations: number;
      uniquePhones: number;
      contacts: number;
    };
    findings: string[];
  };
}

/*
 * Generate a new report from a device.
 */

export async function getDeviceReport(
  deviceId: number
): Promise<InvestigationReport> {

  const { data } =
    await api.get<InvestigationReport>(
      `/reports/device/${deviceId}`
    );

  return data;
}

/*
 * Get all permanently stored reports.
 */

export async function getReports(): Promise<StoredReport[]> {

  const { data } =
    await api.get<StoredReport[] | StoredReportsResponse>(
      "/reports"
    );

  return Array.isArray(data) ? data : data.content ?? data.data ?? [];
}

/*
 * Get one stored report.
 */

export async function getReport(
  reportId: number
): Promise<StoredReport> {

  const { data } =
    await api.get<StoredReport>(
      `/reports/${reportId}`
    );

  return data;
}

/*
 * Get reports belonging to a device.
 */

export async function getDeviceStoredReports(
  deviceId: number
): Promise<StoredReport[]> {

  const { data } =
    await api.get<StoredReport[]>(
      `/reports/device/${deviceId}/stored`
    );

  return data;
}

/*
 * Delete a stored report.
 */

export async function deleteReport(
  reportId: number
): Promise<void> {

  await api.delete(
    `/reports/${reportId}`
  );
}