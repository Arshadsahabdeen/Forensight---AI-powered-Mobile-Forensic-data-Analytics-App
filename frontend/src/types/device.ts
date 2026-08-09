export type DeviceStatus =
  | 'SEIZED'
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

  caseId: number;

  caseNumber: string;

  manufacturer: string;

  brand: string;

  model: string;

  deviceName: string;

  imei: string;

  serialNumber: string;

  operatingSystem: string;

  osVersion: string;

  deviceType: string;

  ownerName: string;

  ownerContact: string;

  investigator: string;

  acquisitionMethod: AcquisitionMethod;

  acquisitionStatus: DeviceStatus;

  seizedDate: string;

  receivedDate: string;

  notes: string;

  createdAt: string;

  updatedAt: string;
}

export interface DeviceListItem {

  id: number;

  deviceName: string;

  manufacturer: string;

  model: string;

  imei: string;

  operatingSystem: string;

  acquisitionStatus: DeviceStatus;

  investigator: string;

  caseNumber: string;

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