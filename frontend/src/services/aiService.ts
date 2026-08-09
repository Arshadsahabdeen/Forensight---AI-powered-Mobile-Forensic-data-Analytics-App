import { api } from "./api";

export interface AIAnalysisResponse {
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
}

export async function analyzeDevice(
  deviceId: number
): Promise<AIAnalysisResponse> {
  const { data } = await api.get(
    `/ai/device/${deviceId}/analyze`
  );

  return data;
}