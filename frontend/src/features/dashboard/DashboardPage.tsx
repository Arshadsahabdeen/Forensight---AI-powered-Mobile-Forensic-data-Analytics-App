import { motion } from 'framer-motion';
import axios from 'axios';
import {
  Activity,
  AlertTriangle,
  Bot,
  CheckCircle2,
  ClipboardList,
  Database,
  FileText,
  HardDrive,
  ShieldCheck,
  UserCircle,
  Users
} from 'lucide-react';
import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { AreaPanel, BarPanel, HorizontalBarPanel, LinePanel, PiePanel } from '../../components/dashboard/ChartPanel';
import { StatCard } from '../../components/dashboard/StatCard';
import { Card } from '../../components/ui/Card';
import { useAuth } from '../../contexts/AuthContext';
import { getDashboard } from '../../services/dashboardService';
import { clearStoredAuth } from '../../services/api';
import { friendlyError } from '../../services/errors';

export function DashboardPage() {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const navigate = useNavigate();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboard,
    enabled: !isAuthLoading && isAuthenticated,
    retry: false
  });

  useEffect(() => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearStoredAuth();
      navigate('/login', { replace: true });
    }
  }, [error, navigate]);

  const iconMap = {
    ClipboardList,
    ShieldCheck,
    CheckCircle2,
    AlertTriangle,
    HardDrive,
    Database,
    Users,
    FileText,
    Activity,
    Bot
  } as const;

  if (isLoading) {
    return <Card className="text-sm text-slate-300">Loading dashboard from backend...</Card>;
  }

  if (!isAuthenticated && !isAuthLoading) {
    return <Card className="text-sm text-slate-300">Redirecting to sign in...</Card>;
  }

  if (error || !data) {
    return (
      <Card>
        <p className="text-sm text-rose-300">{friendlyError(error, 'Unable to load dashboard data.')}</p>
        <button className="mt-3 rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-white/5" onClick={() => refetch()}>
          Retry
        </button>
      </Card>
    );
  }

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
      <div className="mb-6 flex flex-col justify-between gap-3 md:flex-row md:items-end">
        <div>
          <h1 className="text-2xl font-semibold text-white">Investigation Dashboard</h1>
          <p className="mt-1 text-sm text-slate-400">Live operational overview from the forensic backend.</p>
        </div>
        <div className="rounded-md border border-signal/30 bg-signal/10 px-3 py-2 text-sm text-signal">AI Processing: {data.aiProcessingStatus}</div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {data.summaryCards.map((card, index) => {
          const Icon = iconMap[card.icon as keyof typeof iconMap] ?? UserCircle;
          return (
            <motion.div key={card.key} initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.04 }}>
              <StatCard
                label={card.label}
                value={card.key === 'aiProcessingStatus' ? `${card.count}%` : card.count}
                icon={Icon}
                detail="Trend over recent activity window"
                percentageChange={card.percentageChange}
                trend={card.trend}
                color={card.color}
              />
            </motion.div>
          );
        })}
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-2">
        <PiePanel title="Cases by Status" data={data.casesByStatus} />
        <BarPanel title="Cases by Priority" data={data.casesByPriority} />
        <PiePanel title="Evidence by Type" data={data.evidenceByType} />
        <LinePanel title="Monthly Investigation Activity" data={data.monthlyInvestigationActivity} />
        <AreaPanel title="Recent Evidence Upload Trend" data={data.recentEvidenceUploadTrend} />
        <HorizontalBarPanel title="Investigator Workload" data={data.investigatorWorkload} />
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
        <Card className="h-[520px] flex flex-col overflow-hidden">

  <div className="border-b border-slate-800 px-1 pb-3">
    <h3 className="text-sm font-semibold text-slate-100">
      Recent Activity Timeline
    </h3>
  </div>

  <div className="mt-3 flex-1 overflow-y-auto pr-2">

    {data.recentActivities.length === 0 ? (

      <p className="text-sm text-slate-500">
        No recent activity available.
      </p>

    ) : (

      <div className="space-y-3">

        {data.recentActivities.map((activity) => (

          <div
            key={activity.id}
            className="flex gap-3 rounded-md border border-slate-800 bg-white/[0.02] p-3"
          >

            <div className="mt-0.5 rounded-md bg-cyanline/10 p-1.5 text-cyanline">

              {activity.action.includes("LOGIN") ? (
                <ShieldCheck className="h-4 w-4" />
              ) : activity.action.includes("CASE") ? (
                <ClipboardList className="h-4 w-4" />
              ) : activity.action.includes("DEVICE") ? (
                <HardDrive className="h-4 w-4" />
              ) : activity.action.includes("EVIDENCE") ? (
                <Database className="h-4 w-4" />
              ) : (
                <FileText className="h-4 w-4" />
              )}

            </div>

            <div className="min-w-0 flex-1">

              <p className="text-sm font-medium text-slate-200">
                {activity.action}
              </p>

              <p className="text-sm text-slate-400 break-words">
                {activity.description}
              </p>

              <p className="mt-1 text-xs text-slate-500">
                {activity.actor} •{" "}
                {new Date(activity.timestamp).toLocaleString()}
              </p>

            </div>

          </div>

        ))}

      </div>

    )}

  </div>

</Card>

        <Card className="h-[520px] flex flex-col">

  <h3 className="mb-4 text-sm font-semibold text-slate-100">
    Quick Actions
  </h3>

  <div className="flex-1 flex flex-col gap-3">

    {[
      { label: 'Create Case', to: '/cases/new', enabled: true },
      { label: 'Add Device', to: '/devices', enabled: true },
      { label: 'Upload Evidence', to: '/evidence', enabled: true },
      { label: 'Generate Report', to: '/reports', enabled: true },
      { label: 'Open AI Analysis', to: '/ai', enabled: true }
    ].map((action) => (

      <Link
        key={action.label}
        to={action.to}
        className="flex-1 rounded-md border border-slate-800 bg-white/[0.02] p-4 transition hover:border-cyanline/40 hover:bg-white/[0.04]"
      >

        <p className="text-sm font-semibold text-slate-100">
          {action.label}
        </p>

        <p className="mt-2 text-xs text-slate-500">
          {action.enabled ? "Open module" : "Coming Soon"}
        </p>

      </Link>

    ))}

  </div>

</Card>
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-2">
        <Card>
          <h3 className="mb-4 text-sm font-semibold text-slate-100">Investigator Workload</h3>
          <div className="space-y-2">
            {data.investigatorWorkload.slice(0, 5).map((item) => (
              <div key={item.label} className="flex items-center justify-between rounded-md border border-slate-800 bg-white/[0.02] p-3">
                <div>
                  <p className="text-sm font-medium text-slate-100">{item.label}</p>
                  <p className="text-xs text-slate-500">Assigned cases</p>
                </div>
                <span className="rounded-md bg-cyanline/10 px-2 py-1 text-xs text-cyanline">{item.value}</span>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <h3 className="mb-4 text-sm font-semibold text-slate-100">Recent Backend Activity</h3>
          <div className="space-y-2">
            {data.recentActivities.slice(0, 5).map((item) => (
              <div key={item.id} className="flex items-center justify-between gap-3 rounded-md border border-slate-800 bg-white/[0.02] p-3">
                <div>
                  <p className="text-sm font-medium text-slate-100">{item.action}</p>
                  <p className="text-xs text-slate-500">{item.description}</p>
                </div>
                <span className="shrink-0 rounded-md bg-signal/10 px-2 py-1 text-xs text-signal">{new Date(item.timestamp).toLocaleDateString()}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </motion.div>
  );
}
