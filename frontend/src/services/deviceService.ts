import { api } from "./api";
import type {
  Device,
  DeviceDetail,
  DevicePage
} from "../types/device";

export async function getDevices(
  page: number = 0,
  size: number = 10,
  search: string = "",
  status: string = "",
  operatingSystem: string = "",
  caseId?: number
): Promise<DevicePage> {

  const { data } = await api.get<DevicePage>("/devices", {
    params: {
      page,
      size,
      search,
      status,
      operatingSystem,
      caseId
    }
  });

  return data;
}

export async function getDevice(id: number): Promise<DeviceDetail> {

  const { data } = await api.get<DeviceDetail>(
    `/devices/${id}`
  );

  return data;
}

export async function createDevice(
  device: Partial<Device>
): Promise<Device> {

  const { data } = await api.post<Device>(
    "/devices",
    device
  );

  return data;
}

export async function updateDevice(
  id: number,
  device: Partial<Device>
): Promise<Device> {

  const { data } = await api.put<Device>(
    `/devices/${id}`,
    device
  );

  return data;
}

export async function deleteDevice(
  id: number
): Promise<void> {

  await api.delete(`/devices/${id}`);

}