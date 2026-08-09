import { api } from "./api";
import type { TimelineEvent } from "../types/timeline";

export async function getDeviceTimeline(
  deviceId: number
): Promise<TimelineEvent[]> {
  const { data } = await api.get(
    `/timeline/device/${deviceId}`
  );

  return data;
}