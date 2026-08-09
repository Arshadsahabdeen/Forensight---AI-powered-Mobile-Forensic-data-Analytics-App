import { mockDevices } from './devices';

export interface MockEvidence {
  id: number;
  caseId: number;
  deviceId: number;
  fileName: string;
  fileType: 'Image' | 'Video' | 'Audio' | 'PDF' | 'SQLite' | 'JSON' | 'ZIP' | 'CSV';
  category: 'Media' | 'Communication' | 'Location' | 'System' | 'Financial';
  size: string;
  uploadedBy: string;
  uploadDate: string;
  sha256: string;
  status: 'Uploaded' | 'Verified' | 'Flagged' | 'In Review';
}

const types: MockEvidence['fileType'][] = ['Image', 'Video', 'Audio', 'PDF', 'SQLite', 'JSON', 'ZIP', 'CSV'];
const categories: MockEvidence['category'][] = ['Media', 'Communication', 'Location', 'System', 'Financial'];

export const mockEvidence: MockEvidence[] = mockDevices.flatMap((device, index) => {
  const count = index % 3 === 0 ? 1 : 2;
  return Array.from({ length: count }).map((_, local) => {
    const type = types[(index + local) % types.length];
    const id = index * 2 + local + 1;
    return {
      id,
      caseId: device.caseId,
      deviceId: device.id,
      fileName: `case-${String(device.caseId).padStart(3, '0')}-${String(id).padStart(4, '0')}.${type.toLowerCase() === 'sqlite' ? 'db' : type.toLowerCase()}`,
      fileType: type,
      category: categories[(index + local) % categories.length],
      size: `${(12 + ((index + local) % 70)).toFixed(0)} MB`,
      uploadedBy: `Investigator ${((device.caseId - 1) % 10) + 1}`,
      uploadDate: `2026-06-${String(((index + local) % 28) + 1).padStart(2, '0')}T10:30:00Z`,
      sha256: `sha256-${String(device.caseId).padStart(3, '0')}-${String(device.id).padStart(3, '0')}-${String(id).padStart(4, '0')}`,
      status: local % 4 === 0 ? 'Uploaded' : local % 4 === 1 ? 'Verified' : local % 4 === 2 ? 'Flagged' : 'In Review'
    };
  });
});
