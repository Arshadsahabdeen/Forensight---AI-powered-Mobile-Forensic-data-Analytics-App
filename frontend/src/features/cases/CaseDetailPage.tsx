import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card } from '../../components/ui/Card';
import { getCaseById } from '../../services/caseService';
import { friendlyError } from '../../services/errors';

const tabs = ['overview', 'investigators', 'devices', 'evidence', 'timeline', 'reports', 'notes', 'activity'] as const;
type Tab = (typeof tabs)[number];

function label(value: string) {
  return value.replace('_', ' ');
}

export function CaseDetailPage() {
  const { id } = useParams();
  const [activeTab, setActiveTab] = useState<Tab>('overview');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['case', id],
    queryFn: () => getCaseById(id!),
    enabled: Boolean(id)
  });

  if (isLoading) {
    return <Card className="text-sm text-slate-300">Loading case from backend...</Card>;
  }

  if (error || !data) {
    return (
      <Card>
        <p className="text-sm text-rose-300">{friendlyError(error, 'Unable to load case details.')}</p>
        <button className="mt-3 rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-white/5" onClick={() => refetch()}>
          Retry
        </button>
      </Card>
    );
  }

  const { overview, investigators, devices, evidence, timeline, reports, activity } = data;

  return (
    <div className="space-y-5">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
        <div>
          <h1 className="text-2xl font-semibold text-white">{overview.caseNumber} - {overview.title}</h1>
          <p className="mt-1 text-sm text-slate-400">Case detail workspace with independently loaded tabs.</p>
        </div>
        <div className="flex gap-2">
          <Link className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-white/5" to="/cases">Back to Cases</Link>
          <Link className="rounded-md bg-cyanline px-3 py-2 text-sm font-semibold text-ink" to={`/cases/${overview.id}/edit`}>Edit Case</Link>
        </div>
      </div>

      <Card>
        <div className="flex flex-wrap gap-2">
          {tabs.map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`rounded-md px-3 py-2 text-sm ${activeTab === tab ? 'bg-cyanline/20 text-cyanline' : 'bg-white/[0.03] text-slate-300 hover:bg-white/[0.06]'}`}
            >
              {tab.toUpperCase()}
            </button>
          ))}
        </div>
      </Card>

      <Card>
        {activeTab === 'overview' && (
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Status" value={label(overview.status)} />
            <Field label="Priority" value={label(overview.priority)} />
            <Field label="Lead Investigator" value={overview.leadInvestigator} />
            <Field label="Category" value={overview.category} />
            <Field label="Case Type" value={overview.caseType} />
            <Field label="Location" value={overview.location || 'N/A'} />
            <Field label="Created" value={new Date(overview.createdAt).toLocaleString()} />
            <Field label="Updated" value={new Date(overview.updatedAt).toLocaleString()} />
            <div className="md:col-span-2">
              <p className="text-xs uppercase tracking-wide text-slate-500">Description</p>
              <p className="mt-1 text-sm text-slate-200">{overview.description}</p>
            </div>
          </div>
        )}

        {activeTab === 'investigators' && <SimpleList items={investigators.map((item) => `${item.name} - ${item.role} (${item.assignmentType})`)} empty="No assigned investigators." />}
        {activeTab === 'devices' && <SimpleList items={devices.map((item) => `${item.deviceName} - ${item.platform} - ${item.acquisitionStatus}`)} empty="No linked devices." />}
        {activeTab === 'evidence' && <SimpleList items={evidence.map((item) => `${item.artifactType} - ${item.fileName} - ${item.hashSha256 || 'No hash'} (${item.deviceName || 'No device'})`)} empty="No evidence uploaded." />}
        {activeTab === 'timeline' && <SimpleList items={timeline.map((item) => `${item.action} - ${item.description} (${new Date(item.timestamp).toLocaleString()})`)} empty="No timeline events." />}
        {activeTab === 'reports' && <SimpleList items={reports.map((item) => `${item.reportNumber} - ${item.status} (${item.generatedAt ? new Date(item.generatedAt).toLocaleString() : 'Not generated'})`)} empty="No reports generated." />}
        {activeTab === 'notes' && <p className="text-sm text-slate-200">{data.notes || 'No notes recorded.'}</p>}
        {activeTab === 'activity' && <SimpleList items={activity.map((item) => `${item.actor}: ${item.action} - ${item.description}`)} empty="No activity recorded." />}
      </Card>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-sm text-slate-200">{value}</p>
    </div>
  );
}

function SimpleList({ items, empty }: { items: string[]; empty: string }) {
  if (!items.length) {
    return <p className="text-sm text-slate-500">{empty}</p>;
  }
  return (
    <ul className="space-y-2 text-sm text-slate-200">
      {items.map((item) => <li key={item} className="rounded-md border border-slate-800 bg-white/[0.02] px-3 py-2">{item}</li>)}
    </ul>
  );
}
