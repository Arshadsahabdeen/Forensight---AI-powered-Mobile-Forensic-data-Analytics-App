import { useState } from "react";
import {
  Brain,
  Clock,
  FolderOpen,
  LayoutDashboard,
  Layers3,
  Network,
  ShieldCheck
} from "lucide-react";

import { Card } from "../../../components/ui/Card";
import { DeviceTimeline } from "../../timeline/DeviceTimeline";
import { AIAnalysisPanel } from "../../ai/AIAnalysisPanel";
import { DeviceEvidencePanel } from "../../evidence/DeviceEvidencePanel";
import { NormalizedEvidencePanel } from "../../normalizedEvidence/NormalizedEvidencePanel";
import { DeviceRelationshipsPanel } from "./DeviceRelationshipsPanel";
import type { DeviceDetail } from "../../../types/device";

interface InvestigationTabsProps {
  deviceId: number;
  deviceDetail: DeviceDetail;
}

type Tab =
  | "overview"
  | "evidence"
  | "normalizedEvidence"
  | "timeline"
  | "relationships"
  | "ai";

export function InvestigationTabs({
  deviceId,
  deviceDetail
}: InvestigationTabsProps) {
  const [activeTab, setActiveTab] = useState<Tab>("overview");

  const tabs = [
    { id: "overview" as Tab, label: "Overview", icon: LayoutDashboard },
    { id: "evidence" as Tab, label: "Evidence", icon: FolderOpen },
    {
      id: "normalizedEvidence" as Tab,
      label: "Normalized Evidence",
      icon: Layers3
    },
    { id: "timeline" as Tab, label: "Timeline", icon: Clock },
    { id: "relationships" as Tab, label: "Relationships", icon: Network },
    { id: "ai" as Tab, label: "AI Analysis", icon: Brain }
  ];

  return (
    <div className="space-y-6">
      <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-950/70 p-1">
        <div className="flex min-w-max gap-1">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;

            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2 whitespace-nowrap rounded-md px-4 py-2.5 text-sm font-medium transition ${
                  active
                    ? "bg-cyan-500/15 text-cyan-200 shadow-inner shadow-cyan-950/40"
                    : "text-slate-400 hover:bg-slate-900 hover:text-white"
                }`}
              >
                <Icon size={17} />
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {activeTab === "overview" && (
        <OverviewPanel detail={deviceDetail} />
      )}

      {activeTab === "evidence" && (
        <DeviceEvidencePanel deviceId={deviceId} />
      )}

      {activeTab === "normalizedEvidence" && (
        <NormalizedEvidencePanel deviceId={deviceId} />
      )}

      {activeTab === "timeline" && (
        <DeviceTimeline deviceId={deviceId} />
      )}

      {activeTab === "relationships" && (
        <DeviceRelationshipsPanel deviceId={deviceId} />
      )}

      {activeTab === "ai" && (
        <AIAnalysisPanel deviceId={deviceId} />
      )}
    </div>
  );
}

function OverviewPanel({ detail }: { detail: DeviceDetail }) {
  const device = detail.device;

  return (
    <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
      <Card className="bg-slate-950/70">
        <div className="flex gap-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-cyan-500/20 bg-cyan-500/10 text-cyan-300">
            <ShieldCheck size={22} />
          </div>

          <div>
            <h2 className="text-lg font-semibold text-white">
              Investigation Overview
            </h2>

            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-400">
              Review extracted evidence, chronological activity, communication
              relationships, and AI-assisted findings for {device.deviceName ?? "this device"}.
            </p>
          </div>
        </div>
      </Card>

      <Card className="bg-slate-950/70">
        <div className="grid grid-cols-3 gap-3">
          <QuickStat label="Evidence" value={String(detail.evidenceCount)} />
          <QuickStat label="Reports" value={String(detail.reportCount)} />
          <QuickStat label="Timeline" value={String(detail.timelineCount)} />
        </div>
      </Card>
    </div>
  );
}

function QuickStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-4 text-center">
      <div className="text-xs uppercase tracking-wider text-slate-500">{label}</div>
      <div className="mt-2 text-2xl font-bold text-white">{value}</div>
    </div>
  );
}
