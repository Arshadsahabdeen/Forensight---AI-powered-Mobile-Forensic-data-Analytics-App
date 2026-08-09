import { Card } from '../ui/Card';
import type { Activity } from '../../types/dashboard';

export function RecentCasesTable({ cases }: { cases: Array<{ id: number; caseNumber: string; title: string; status: string; leadInvestigator: string; updatedAt: string }> }) {
  return (
    <Card>
      <h3 className="mb-4 text-sm font-semibold text-slate-100">Recent Cases</h3>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase text-slate-500">
            <tr><th className="pb-3">Case</th><th className="pb-3">Title</th><th className="pb-3">Status</th><th className="pb-3">Lead</th><th className="pb-3">Updated</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {cases.map((item) => (
              <tr key={item.id} className="text-slate-300">
                <td className="py-3 font-medium text-cyanline">{item.caseNumber}</td>
                <td className="py-3">{item.title}</td>
                <td className="py-3"><span className="rounded-md bg-signal/10 px-2 py-1 text-xs text-signal">{item.status}</span></td>
                <td className="py-3">{item.leadInvestigator}</td>
                <td className="py-3 text-slate-500">{new Date(item.updatedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

export function AuditLogTable({ logs }: { logs: Activity[] }) {
  return (
    <Card>
      <h3 className="mb-4 text-sm font-semibold text-slate-100">Recent Audit Logs</h3>
      <div className="space-y-3">
        {logs.map((log) => (
          <div key={log.id} className="rounded-md border border-slate-800 bg-white/[0.02] p-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm font-medium text-slate-200">{log.action}</span>
              <span className="text-xs text-slate-500">{new Date(log.timestamp).toLocaleString()}</span>
            </div>
            <p className="mt-1 text-sm text-slate-400">{log.description}</p>
            <p className="mt-1 text-xs text-cyanline">{log.actor}</p>
          </div>
        ))}
      </div>
    </Card>
  );
}
