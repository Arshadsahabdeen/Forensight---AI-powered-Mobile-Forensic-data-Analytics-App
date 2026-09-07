export type DeviceStatus =
  | 'SEIZED'
  | 'ACQUIRED'
  | 'WAITING_FOR_EXTRACTION'
  | 'EXTRACTION_COMPLETED'
  | 'UNDER_ANALYSIS'
  | 'REPORT_GENERATED'
  | 'ARCHIVED';

export type AcquisitionMethod =
  | 'LOGICAL_EXTRACTION'
  | 'FILE_SYSTEM'
  | 'PHYSICAL_EXTRACTION'
  | 'CHIP_OFF'
  | 'JTAG';

export interface Device {

  id: number;

  caseId: number | null;

  caseNumber: string | null;

  manufacturer: string | null;

  brand: string | null;

  model: string | null;

  deviceName: string | null;

  imei: string | null;

  serialNumber: string | null;

  operatingSystem: string | null;

  osVersion: string | null;

  deviceType: string | null;

  ownerName: string | null;

  ownerContact: string | null;

  investigator: string | null;

  acquisitionMethod: AcquisitionMethod;

  acquisitionStatus: DeviceStatus;

  seizedDate: string | null;

  receivedDate: string | null;

  notes: string | null;

  createdAt: string;

  updatedAt: string;
}

export interface DeviceListItem {

  id: number;

  deviceName: string | null;

  manufacturer: string | null;

  model: string | null;

  imei: string | null;

  operatingSystem: string | null;

  acquisitionStatus: DeviceStatus;

  investigator: string | null;

  caseNumber: string | null;

  updatedAt: string;
}

export interface DevicePage {

  items: DeviceListItem[];

  totalElements: number;

  totalPages: number;

  page: number;

  size: number;

  first: boolean;

  last: boolean;
}

export interface DeviceDetail {

  device: Device;

  evidenceCount: number;

  reportCount: number;

  timelineCount: number;
}
