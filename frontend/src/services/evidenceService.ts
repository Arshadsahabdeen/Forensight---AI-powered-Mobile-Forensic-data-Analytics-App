import { api } from "./api";

export interface Evidence {
  id: number; artifactType: string | null; fileName: string | null; hashSha256: string | null;
  title: string | null; content: string | null; source: string | null; target: string | null;
  phoneNumber: string | null; contactName: string | null; location: string | null;
  latitude: number | null; longitude: number | null; eventTime: string | null; createdAt: string | null;
  deviceId: number | null; deviceName: string | null; caseId: number | null; caseNumber: string | null;
}
export interface EvidenceQuery { page: number; size: number; sort: "eventTime" | "createdAt" | "id" | "artifactType"; direction: "asc" | "desc"; search?: string; artifactType?: string; deviceId?: number; caseId?: number; startTime?: string; endTime?: string; source?: string; location?: string; }
export interface EvidencePage { items: Evidence[]; totalElements: number; totalPages: number; page: number; size: number; first: boolean; last: boolean; }
export interface RelatedEvidence { id: number; relationshipType: "DIRECT_RELATIONSHIP"; relationship: "SAME_DEVICE"; evidence: Evidence; }

/*
 * Older running backend images return a raw Evidence[] while the current
 * repository endpoint returns EvidencePage.  Normalising here keeps the
 * repository and device views connected to the same collected device data.
 */
function asPage(data: EvidencePage | Evidence[], params: EvidenceQuery): EvidencePage {
  if (!Array.isArray(data)) return data;
  const term = params.search?.toLowerCase();
  const filtered = data.filter(item => {
    const searchable = [item.title, item.content, item.artifactType, item.source, item.target, item.phoneNumber, item.contactName, item.location].join(" ").toLowerCase();
    return (!term || searchable.includes(term))
      && (!params.artifactType || item.artifactType?.toLowerCase() === params.artifactType.toLowerCase())
      && (!params.deviceId || item.deviceId === params.deviceId)
      && (!params.caseId || item.caseId === params.caseId);
  }).sort((a, b) => {
    const first = params.sort === "eventTime" ? a.eventTime : params.sort === "artifactType" ? a.artifactType : a.id;
    const second = params.sort === "eventTime" ? b.eventTime : params.sort === "artifactType" ? b.artifactType : b.id;
    const comparison = String(first ?? "").localeCompare(String(second ?? ""));
    return params.direction === "desc" ? -comparison : comparison;
  });
  const totalElements = filtered.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / params.size));
  const start = params.page * params.size;
  return { items: filtered.slice(start, start + params.size), totalElements, totalPages, page: params.page, size: params.size, first: params.page === 0, last: start + params.size >= totalElements };
}

function compact(params: EvidenceQuery): Record<string, string | number> {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== "")) as Record<string, string | number>;
}
export async function getEvidence(params: EvidenceQuery): Promise<EvidencePage> { const { data } = await api.get<EvidencePage | Evidence[]>("/evidence", { params: compact(params) }); return asPage(data, params); }
export async function getEvidenceCount(params: Omit<EvidenceQuery, "page" | "size" | "sort" | "direction">): Promise<number> { const { data } = await api.get<{ totalElements: number }>("/evidence/count", { params: compact({ ...params, page: 0, size: 1, sort: "id", direction: "asc" }) }); return data.totalElements; }
export async function getEvidenceById(id: number): Promise<Evidence> { const { data } = await api.get<Evidence>(`/evidence/${id}`); return data; }
export async function getRelatedEvidence(id: number): Promise<RelatedEvidence[]> { const { data } = await api.get<RelatedEvidence[]>(`/evidence/${id}/related`); return data; }
export async function getDeviceEvidence(deviceId: number, params: Omit<EvidenceQuery, "deviceId">): Promise<EvidencePage> { const query = { ...params, deviceId } as EvidenceQuery; const { data } = await api.get<EvidencePage | Evidence[]>(`/evidence/device/${deviceId}`, { params: compact(params) }); return asPage(data, query); }
