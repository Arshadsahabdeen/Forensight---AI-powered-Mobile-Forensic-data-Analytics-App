import { api } from "./api";
import type {
  NormalizedEvidence,
  NormalizedEvidenceCategory,
} from "../types/normalizedEvidence";

export async function getNormalizedEvidenceByDevice(
  deviceId: number
): Promise<NormalizedEvidence[]> {
  const { data } = await api.get<NormalizedEvidence[]>(
    `/normalized-evidence/device/${deviceId}`
  );

  return data;
}

export async function getNormalizedEvidenceByDeviceAndCategory(
  deviceId: number,
  category: NormalizedEvidenceCategory
): Promise<NormalizedEvidence[]> {
  const { data } = await api.get<NormalizedEvidence[]>(
    `/normalized-evidence/device/${deviceId}/category/${category}`
  );

  return data;
}

export async function getNormalizedEvidenceById(
  id: number
): Promise<NormalizedEvidence> {
  const { data } = await api.get<NormalizedEvidence>(
    `/normalized-evidence/${id}`
  );

  return data;
}
