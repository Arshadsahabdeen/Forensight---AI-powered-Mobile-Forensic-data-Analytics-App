import { mockCases } from './cases';
import { mockDevices } from './devices';
import { mockEvidence } from './evidence';

export interface MockTimelineEvent {
  id: number;
  caseId: number;
  title: string;
  description: string;
  timestamp: string;
  user: string;
  icon: 'case' | 'device' | 'upload' | 'verify' | 'report' | 'close';
}

function byCase(caseId: number) {
  return {
    devices: mockDevices.filter((item) => item.caseId === caseId),
    evidence: mockEvidence.filter((item) => item.caseId === caseId)
  };
}

export const mockTimeline: MockTimelineEvent[] = mockCases.flatMap((item, index) => {
  const related = byCase(item.id);
  const events: MockTimelineEvent[] = [
    {
      id: index * 100 + 1,
      caseId: item.id,
      title: 'Case Created',
      description: `${item.caseNumber} initialized and assigned to ${item.leadInvestigator}`,
      timestamp: item.createdDate,
      user: item.leadInvestigator,
      icon: 'case'
    }
  ];

  if (related.devices[0]) {
    events.push({
      id: index * 100 + 2,
      caseId: item.id,
      title: 'Device Added',
      description: `${related.devices[0].model} seized and registered`,
      timestamp: item.updatedDate,
      user: item.leadInvestigator,
      icon: 'device'
    });
  }

  if (related.evidence[0]) {
    events.push(
      {
        id: index * 100 + 3,
        caseId: item.id,
        title: 'Evidence Uploaded',
        description: `${related.evidence[0].fileName} uploaded`,
        timestamp: related.evidence[0].uploadDate,
        user: related.evidence[0].uploadedBy,
        icon: 'upload'
      },
      {
        id: index * 100 + 4,
        caseId: item.id,
        title: 'Evidence Verified',
        description: `Integrity verified for ${related.evidence[0].sha256}`,
        timestamp: related.evidence[0].uploadDate,
        user: item.leadInvestigator,
        icon: 'verify'
      }
    );
  }

  events.push({
    id: index * 100 + 5,
    caseId: item.id,
    title: 'Report Generated',
    description: `Case report status moved to ${item.reportStatus}`,
    timestamp: item.updatedDate,
    user: item.leadInvestigator,
    icon: 'report'
  });

  if (item.status === 'Closed') {
    events.push({
      id: index * 100 + 6,
      caseId: item.id,
      title: 'Case Closed',
      description: `${item.caseNumber} closed after review`,
      timestamp: item.updatedDate,
      user: item.leadInvestigator,
      icon: 'close'
    });
  }

  return events;
}).sort((left, right) => (left.timestamp < right.timestamp ? 1 : -1));
