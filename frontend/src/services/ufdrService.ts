import { api } from "./api";

export interface UFDRUploadResponse {
  status: string;
  message: string;
  deviceId: number;
  evidenceCount: number;
  fileName?: string;
  size?: number;
  device?: {
    manufacturer?: string;
    brand?: string;
    model?: string;
    imei?: string;
    operatingSystem?: string;
  };
}

export async function uploadUFDR(
  file: File
): Promise<UFDRUploadResponse> {

  const formData = new FormData();

  formData.append("file", file);

  const { data } = await api.post(
    "/ufdr/upload",
    formData,
    {
      headers: {
        "Content-Type": "multipart/form-data"
      }
    }
  );

  return data;
}