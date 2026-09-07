export type NormalizedEvidenceCategory =
  | "COMMUNICATION"
  | "LOCATION"
  | "CONTACT"
  | "MEDIA"
  | "BROWSER"
  | "ACCOUNT";

export interface NormalizedEvidence {
  id: number;
  artifactType: string;
  category: NormalizedEvidenceCategory;
  sourceTool: string;
  deviceId: number;
  caseId: number;
  title: string | null;
  content: string | null;
  metadata: Record<string, unknown>;
  eventTime: string | null;
  createdAt: string;
}
