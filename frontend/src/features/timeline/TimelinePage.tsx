import { CheckCircle2, ClipboardList, Database, FileCheck2, HardDrive, ScrollText } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { mockCases } from '../../mock/cases';
import { mockTimeline } from '../../mock/timeline';

function icon(name: string) {
  if (name === 'case') return <ClipboardList className="h-4 w-4 text-cyanline" />;
  if (name === 'device') return <HardDrive className="h-4 w-4 text-signal" />;
  if (name === 'upload') return <Database className="h-4 w-4 text-amber-300" />;
  if (name === 'verify') return <FileCheck2 className="h-4 w-4 text-blue-300" />;
  if (name === 'close') return <CheckCircle2 className="h-4 w-4 text-emerald-300" />;
  return <ScrollText className="h-4 w-4 text-violet-300" />;
}

export function TimelinePage() {
  const caseMap = new Map(mockCases.map((item) => [item.id, item]));

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-white">Investigation Timeline</h1>
        <p className="mt-1 text-sm text-slate-400">Chronological forensic activity stream from connected mock datasets.</p>
      </div>

      <Card>
        <div className="space-y-3">
          {mockTimeline.map((event) => (
            <div key={event.id} className="flex gap-3 rounded-md border border-slate-800 bg-white/[0.02] p-3">
              <div className="mt-1">{icon(event.icon)}</div>
              <div>
                <p className="text-sm font-medium text-slate-100">{event.title} - {caseMap.get(event.caseId)?.caseNumber}</p>
                <p className="text-sm text-slate-400">{event.description}</p>
                <p className="mt-1 text-xs text-slate-500">{new Date(event.timestamp).toLocaleString()} | {event.user} | {caseMap.get(event.caseId)?.title}</p>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <div className="text-xs text-slate-500">Linked module: <Link className="text-cyanline" to="/cases">Cases</Link></div>
    </div>
  );
}
