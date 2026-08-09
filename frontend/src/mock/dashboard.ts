import type { DashboardData } from '../types/dashboard';
import { mockCases } from './cases';
import { mockDevices } from './devices';
import { mockEvidence } from './evidence';
import { mockTimeline } from './timeline';
import { mockUsers } from './users';

function grouped<T extends string>(items: T[]) {
  return items.reduce<Record<string, number>>((acc, item) => {
    acc[item] = (acc[item] ?? 0) + 1;
    return acc;
  }, {});
}

function toChart(input: Record<string, number>) {
  return Object.entries(input).map(([label, value]) => ({ label, value }));
}

function trend(current: number, previous: number): 'up' | 'down' | 'flat' {
  if (current === previous) return 'flat';
  return current > previous ? 'up' : 'down';
}

function pct(current: number, previous: number) {
  if (previous <= 0) return current > 0 ? 100 : 0;
  return Number((((current - previous) * 100) / previous).toFixed(1));
}

const totalCases = mockCases.length;
const activeCases = mockCases.filter((item) => item.status === 'Active').length;
const closedCases = mockCases.filter((item) => item.status === 'Closed').length;
const highPriority = mockCases.filter((item) => item.priority === 'High' || item.priority === 'Critical').length;
const totalDevices = mockDevices.length;
const totalEvidence = mockEvidence.length;
const totalInvestigators = mockUsers.length;
const reportsGenerated = mockCases.filter((item) => item.reportStatus === 'Generated').length;
const todaysActivities = mockTimeline.slice(0, 20).length;
const aiProcessing = Math.round((mockEvidence.filter((item) => item.status === 'Verified').length / Math.max(1, totalEvidence)) * 100);

export const mockDashboard: DashboardData = {
  summaryCards: [
    { key: 'totalCases', label: 'Total Cases', count: totalCases, percentageChange: pct(totalCases, 18), trend: trend(totalCases, 18), color: 'cyan', icon: 'ClipboardList' },
    { key: 'activeCases', label: 'Active Cases', count: activeCases, percentageChange: pct(activeCases, 4), trend: trend(activeCases, 4), color: 'blue', icon: 'ShieldCheck' },
    { key: 'closedCases', label: 'Closed Cases', count: closedCases, percentageChange: pct(closedCases, 3), trend: trend(closedCases, 3), color: 'green', icon: 'CheckCircle2' },
    { key: 'highPriorityCases', label: 'High Priority Cases', count: highPriority, percentageChange: pct(highPriority, 7), trend: trend(highPriority, 7), color: 'red', icon: 'AlertTriangle' },
    { key: 'totalDevices', label: 'Total Devices', count: totalDevices, percentageChange: pct(totalDevices, 24), trend: trend(totalDevices, 24), color: 'slate', icon: 'HardDrive' },
    { key: 'totalEvidence', label: 'Total Evidence', count: totalEvidence, percentageChange: pct(totalEvidence, 45), trend: trend(totalEvidence, 45), color: 'amber', icon: 'Database' },
    { key: 'totalInvestigators', label: 'Total Investigators', count: totalInvestigators, percentageChange: pct(totalInvestigators, 8), trend: trend(totalInvestigators, 8), color: 'violet', icon: 'Users' },
    { key: 'reportsGenerated', label: 'Reports Generated', count: reportsGenerated, percentageChange: pct(reportsGenerated, 5), trend: trend(reportsGenerated, 5), color: 'teal', icon: 'FileText' },
    { key: 'todaysActivities', label: "Today's Activities", count: todaysActivities, percentageChange: pct(todaysActivities, 16), trend: trend(todaysActivities, 16), color: 'orange', icon: 'Activity' },
    { key: 'aiProcessingStatus', label: 'AI Processing Status', count: aiProcessing, percentageChange: pct(aiProcessing, 68), trend: trend(aiProcessing, 68), color: 'emerald', icon: 'Bot' }
  ],
  casesByStatus: toChart(grouped(mockCases.map((item) => item.status))),
  casesByPriority: toChart(grouped(mockCases.map((item) => item.priority))),
  evidenceByType: toChart(grouped(mockEvidence.map((item) => item.fileType))),
  monthlyInvestigationActivity: [
    { label: 'Feb', value: 34 },
    { label: 'Mar', value: 49 },
    { label: 'Apr', value: 52 },
    { label: 'May', value: 63 },
    { label: 'Jun', value: 76 },
    { label: 'Jul', value: 68 }
  ],
  recentEvidenceUploadTrend: [
    { label: 'Jul 08', value: 6 },
    { label: 'Jul 09', value: 9 },
    { label: 'Jul 10', value: 11 },
    { label: 'Jul 11', value: 8 },
    { label: 'Jul 12', value: 13 },
    { label: 'Jul 13', value: 10 },
    { label: 'Jul 14', value: 12 }
  ],
  investigatorWorkload: toChart(grouped(mockCases.map((item) => item.leadInvestigator))).sort((left, right) => right.value - left.value),
  recentActivities: mockTimeline.slice(0, 15).map((item) => ({
    id: item.id,
    actor: item.user,
    action: item.title.toUpperCase().replace(/ /g, '_'),
    description: item.description,
    timestamp: item.timestamp
  })),
  aiProcessingStatus: `${aiProcessing}%`
};
