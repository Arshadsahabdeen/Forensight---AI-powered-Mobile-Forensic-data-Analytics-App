import { Database, FileArchive, FileAudio, FileImage, FileJson, FileSpreadsheet, FileText, FileVideo } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { mockCases } from '../../mock/cases';
import { mockDevices } from '../../mock/devices';
import { mockEvidence } from '../../mock/evidence';

function iconForType(fileType: string) {
  switch (fileType) {
    case 'Image': return <FileImage className="h-4 w-4" />;
    case 'Video': return <FileVideo className="h-4 w-4" />;
    case 'Audio': return <FileAudio className="h-4 w-4" />;
    case 'JSON': return <FileJson className="h-4 w-4" />;
    case 'ZIP': return <FileArchive className="h-4 w-4" />;
    case 'CSV': return <FileSpreadsheet className="h-4 w-4" />;
    case 'SQLite': return <Database className="h-4 w-4" />;
    default: return <FileText className="h-4 w-4" />;
  }
}

export function EvidenceListPage() {
  const caseMap = new Map(mockCases.map((item) => [item.id, item]));
  const deviceMap = new Map(mockDevices.map((item) => [item.id, item]));

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-white">Evidence Repository</h1>
        <p className="mt-1 text-sm text-slate-400">Connected static evidence records mapped to cases and devices.</p>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1120px] text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr>
                <th className="pb-3">Filename</th>
                <th className="pb-3">Category</th>
                <th className="pb-3">File Type</th>
                <th className="pb-3">Case</th>
                <th className="pb-3">Device</th>
                <th className="pb-3">Hash</th>
                <th className="pb-3">Upload Date</th>
                <th className="pb-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {mockEvidence.map((item) => (
                <tr key={item.id} className="text-slate-300">
                  <td className="py-3 font-medium text-cyanline">{item.fileName}</td>
                  <td className="py-3">{item.category}</td>
                  <td className="py-3"><span className="inline-flex items-center gap-1 rounded-md bg-white/[0.04] px-2 py-1">{iconForType(item.fileType)}{item.fileType}</span></td>
                  <td className="py-3">{caseMap.get(item.caseId)?.caseNumber}</td>
                  <td className="py-3">{deviceMap.get(item.deviceId)?.model}</td>
                  <td className="py-3 text-xs text-slate-500">{item.sha256}</td>
                  <td className="py-3">{new Date(item.uploadDate).toLocaleString()}</td>
                  <td className="py-3"><span className="rounded-md bg-signal/10 px-2 py-1 text-xs text-signal">{item.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="text-xs text-slate-500">Linked modules: <Link className="text-cyanline" to="/cases">Cases</Link> and <Link className="text-cyanline" to="/devices">Devices</Link></div>
    </div>
  );
}
