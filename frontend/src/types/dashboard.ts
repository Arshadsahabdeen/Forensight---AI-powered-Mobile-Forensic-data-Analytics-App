export interface ChartMetric {
  label: string;
  value: number;
}

export interface Activity {
  id: number;
  actor: string;
  action: string;
  description: string;
  timestamp: string;
}

export interface SummaryCard {
  key: string;
  label: string;
  count: number;
  percentageChange: number;
  trend: 'up' | 'down' | 'flat';
  color: string;
  icon: string;
}

export interface DashboardData {
  summaryCards: SummaryCard[];
  casesByStatus: ChartMetric[];
  casesByPriority: ChartMetric[];
  evidenceByType: ChartMetric[];
  monthlyInvestigationActivity: ChartMetric[];
  recentEvidenceUploadTrend: ChartMetric[];
  investigatorWorkload: ChartMetric[];
  recentActivities: Activity[];
  aiProcessingStatus: string;
}
