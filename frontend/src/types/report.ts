import type { TimelineEvent } from "./timeline";
import type { AIAnalysisResponse } from "../services/aiService";

export interface ReportDevice {
  id: number;
  deviceName: string | null;
  manufacturer: string | null;
  brand: string | null;
  model: string | null;
  imei: string | null;
  operatingSystem: string | null;
}

export interface EvidenceSummary {
  totalEvidence: number;
  byArtifactType: Record<string, number>;
}

export interface RelationshipGraphNode {
  id: string | number;
  label: string;
  type?: string;
}

export interface RelationshipGraphEdge {
  source: string | number;
  target: string | number;
  relationship?: string;
  label?: string;
}

export interface RelationshipGraph {
  nodes: RelationshipGraphNode[];
  edges: RelationshipGraphEdge[];
}

export interface InvestigationReport {
  deviceId: number;
  device: ReportDevice;
  evidenceSummary: EvidenceSummary;
  timeline: TimelineEvent[];
  relationshipGraph: RelationshipGraph;
  aiAnalysis: AIAnalysisResponse;
}