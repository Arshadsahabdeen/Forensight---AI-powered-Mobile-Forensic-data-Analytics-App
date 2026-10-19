import type { AcquisitionMethod, DeviceRequest, DeviceStatus } from '../../types/api';

export const DEVICE_STATUSES: DeviceStatus[] = ['SEIZED', 'ACQUIRED', 'WAITING_FOR_EXTRACTION', 'EXTRACTION_COMPLETED', 'UNDER_ANALYSIS', 'REPORT_GENERATED', 'ARCHIVED'];

export const ACQUISITION_METHODS: AcquisitionMethod[] = [
  'LOGICAL_EXTRACTION', 'FILE_SYSTEM_EXTRACTION', 'PHYSICAL_EXTRACTION', 'CHIP_OFF', 'JTAG', 'MANUAL_COLLECTION', 'IMPORTED_UFDR', 'IMPORTED_AXIOM', 'OTHER'
];

/** Mirrors the backend's DeviceRequest validation. */
export function validateDevice(values: DeviceRequest): Partial<Record<keyof DeviceRequest, string>> {
  const errors: Partial<Record<keyof DeviceRequest, string>> = {};
  if (!values.caseId) errors.caseId = 'Select the case this device belongs to.';
  if (!values.deviceName.trim()) errors.deviceName = 'Device name is required.';
  if (!values.manufacturer.trim()) errors.manufacturer = 'Manufacturer is required.';
  if (!values.model.trim()) errors.model = 'Model is required.';
  if (!values.operatingSystem.trim()) errors.operatingSystem = 'Operating system is required.';
  const imei = (values.imei ?? '').trim();
  if (imei && !/^\d{14,16}$/.test(imei)) errors.imei = 'IMEI must be 14 to 16 digits.';
  if (values.seizedDate && values.receivedDate && values.receivedDate < values.seizedDate) errors.receivedDate = 'Received date cannot be before the seizure date.';
  return errors;
}
