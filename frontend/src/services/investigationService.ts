import { api } from "./api";
import type { TimelineEvent } from "../types/timeline";
import type { RelationshipGraphResponse } from "../types/investigation";

export async function getDeviceTimeline(
  deviceId: number
): Promise<TimelineEvent[]> {
  const { data } = await api.get<TimelineEvent[]>(
    `/timeline/device/${deviceId}`
  );

  return data;
}

export async function getDeviceRelationshipGraph(
  deviceId: number
): Promise<RelationshipGraphResponse> {
  const { data } = await api.get<RelationshipGraphResponse>(
    `/relationship-graph/device/${deviceId}`
  );

  return data;
}

export async function getTimeline(): Promise<TimelineEvent[]> {
  const { data } = await api.get<TimelineEvent[]>("/timeline");

  return data;
}
