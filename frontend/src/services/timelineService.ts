import { api } from "./api";

export interface TimelineArtifact {
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
  deviceId: number;
}

export interface RelationshipNode {
  id: string;
  label: string;
  type: string;
}

export interface RelationshipEdge {
  source: string;
  target: string;
  relationship: string;
  evidenceId: number;
}

export interface DeviceInvestigationResponse {
  deviceId: number;

  device: {
    id: number;
    deviceName: string;
    manufacturer: string;
    brand: string;
    model: string;
    imei: string;
    operatingSystem: string;
  };

  evidenceSummary: {
    totalEvidence: number;
    byArtifactType: Record<string, number>;
  };

  timeline: TimelineArtifact[];

  relationshipGraph: {
    nodes: RelationshipNode[];
    edges: RelationshipEdge[];
  };

  aiAnalysis?: {
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

export async function getDeviceInvestigation(
  deviceId: number
): Promise<DeviceInvestigationResponse> {
  const { data } = await api.get(
    `/timeline/device/${deviceId}`
  );

  return data;
}