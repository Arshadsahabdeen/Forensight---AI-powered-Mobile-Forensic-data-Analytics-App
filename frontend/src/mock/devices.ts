export interface MockDevice {
  id: number;
  caseId: number;
  manufacturer: string;
  brand: string;
  model: string;
  imei: string;
  androidVersion: string;
  serialNumber: string;
  owner: string;
  status: 'Seized' | 'Imaged' | 'Analyzed' | 'Archived';
}

const catalog = [
  ['Samsung', 'Samsung', 'Galaxy S24', '14'],
  ['Google', 'Google', 'Pixel 8', '14'],
  ['OnePlus', 'OnePlus', 'OnePlus 12', '14'],
  ['Xiaomi', 'Xiaomi', 'Xiaomi 14', '14'],
  ['Apple', 'Apple', 'iPhone 15 Pro', 'iOS 17']
] as const;

export const mockDevices: MockDevice[] = Array.from({ length: 30 }).map((_, index) => {
  const item = catalog[index % catalog.length];
  const caseId = index < 20 ? index + 1 : index - 19;
  return {
    id: index + 1,
    caseId,
    manufacturer: item[0],
    brand: item[1],
    model: item[2],
    imei: `35678901${String(index + 100000).padStart(6, '0')}`,
    androidVersion: item[3],
    serialNumber: `SN-FOR-${String(index + 1).padStart(4, '0')}`,
    owner: `Owner ${caseId}`,
    status: index % 4 === 0 ? 'Seized' : index % 4 === 1 ? 'Imaged' : index % 4 === 2 ? 'Analyzed' : 'Archived'
  };
});
