import { useState } from "react";
import { Brain, Database, RefreshCw, ShieldCheck, Smartphone } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import { getDevices } from "../../services/deviceService";
import { AIAnalysisPanel } from "./AIAnalysisPanel";

export function AIAnalysisPage() {
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | null>(null);
  const devices = useQuery({ queryKey: ["ai-devices"], queryFn: () => getDevices(0, 100) });
  const selectedDevice = devices.data?.items.find(device => device.id === selectedDeviceId);

  return <div className="mx-auto max-w-7xl space-y-5 p-4 md:p-6">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[.18em] text-cyan-300"><Brain size={15}/>Forensic intelligence</p>
        <h1 className="mt-2 text-3xl font-bold text-white">AI Analysis Center</h1>
        <p className="mt-1 max-w-2xl text-slate-400">Run the platform&apos;s evidence analysis engine against a device to extract entities, communications, locations, keywords, and investigation findings.</p>
      </div>
      <Button className="bg-transparent text-slate-300 hover:bg-slate-800" onClick={() => devices.refetch()} disabled={devices.isFetching}><RefreshCw size={16} className={devices.isFetching ? "animate-spin" : ""}/>Refresh devices</Button>
    </div>

    <div className="grid gap-3 sm:grid-cols-3">
      <SummaryCard icon={<Smartphone size={18}/>} label="Devices available" value={devices.data?.items.length ?? 0}/>
      <SummaryCard icon={<Database size={18}/>} label="Analysis input" value={selectedDevice ? (selectedDevice.deviceName || selectedDevice.model || `Device #${selectedDevice.id}`) : "Select a device"}/>
      <SummaryCard icon={<ShieldCheck size={18}/>} label="Engine status" value={devices.isError ? "Unavailable" : "Ready"}/>
    </div>

    <Card>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="font-semibold text-white">Choose evidence source</h2>
          <p className="mt-1 text-sm text-slate-500">The engine analyzes persisted evidence attached to the selected device.</p>
        </div>
        <label className="w-full lg:max-w-xl"><span className="mb-2 block text-xs font-semibold uppercase tracking-wide text-slate-500">Device</span><select value={selectedDeviceId ?? ""} onChange={event => setSelectedDeviceId(event.target.value ? Number(event.target.value) : null)} disabled={devices.isLoading || devices.isError} className="field"><option value="">Select a device to begin</option>{devices.data?.items.map(device => <option key={device.id} value={device.id}>{device.deviceName || device.model || `Device #${device.id}`} · {device.manufacturer || "Unknown manufacturer"} · {device.caseNumber || "No case"}</option>)}</select></label>
      </div>
      {devices.isLoading && <p className="mt-4 text-sm text-slate-400">Loading devices...</p>}
      {devices.isError && <p className="mt-4 text-sm text-red-300">Unable to load devices. Refresh and try again.</p>}
      {!devices.isLoading && !devices.isError && !devices.data?.items.length && <p className="mt-4 text-sm text-slate-400">No devices are available for analysis yet.</p>}
    </Card>

    {selectedDeviceId ? <AIAnalysisPanel deviceId={selectedDeviceId}/> : <Card><div className="flex flex-col items-center justify-center py-14 text-center"><Brain size={36} className="text-cyan-400"/><h2 className="mt-4 text-lg font-semibold text-white">Select a device to run analysis</h2><p className="mt-2 max-w-lg text-sm leading-6 text-slate-500">Results will include a summary, evidence statistics, correlated phone numbers, contacts and locations, extracted keywords, and actionable findings.</p></div></Card>}
  </div>;
}

function SummaryCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: number | string }) {
  return <Card><div className="flex items-center gap-2 text-cyan-400">{icon}<span className="text-xs uppercase tracking-wide text-slate-500">{label}</span></div><p className="mt-3 truncate text-xl font-bold text-white">{value}</p></Card>;
}