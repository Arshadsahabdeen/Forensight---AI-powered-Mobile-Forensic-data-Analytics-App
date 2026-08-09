import { useState } from "react";

import {
  LayoutDashboard,
  Clock,
  Brain,
  Network
} from "lucide-react";

import { DeviceTimeline } from "../../timeline/DeviceTimeline";
import { AIAnalysisPanel } from "../../ai/AIAnalysisPanel";

interface Props {
  deviceId: number;
}

type Tab =
  | "overview"
  | "timeline"
  | "relationships"
  | "ai";

export function InvestigationTabs({
  deviceId
}: Props) {

  const [activeTab, setActiveTab] =
    useState<Tab>("overview");

  const tabs = [
    {
      id: "overview" as Tab,
      label: "Overview",
      icon: LayoutDashboard
    },
    {
      id: "timeline" as Tab,
      label: "Timeline",
      icon: Clock
    },
    {
      id: "relationships" as Tab,
      label: "Relationships",
      icon: Network
    },
    {
      id: "ai" as Tab,
      label: "AI Analysis",
      icon: Brain
    }
  ];

  return (
    <div className="space-y-6">

      <div className="flex gap-2 overflow-x-auto border-b border-slate-800">

        {tabs.map((tab) => {

          const Icon = tab.icon;

          const active =
            activeTab === tab.id;

          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-medium transition ${
                active
                  ? "border-cyan-400 text-cyan-400"
                  : "border-transparent text-slate-400 hover:text-white"
              }`}
            >
              <Icon size={17} />

              {tab.label}
            </button>
          );

        })}

      </div>

      {activeTab === "overview" && (
        <OverviewPlaceholder />
      )}

      {activeTab === "timeline" && (
        <DeviceTimeline
          deviceId={deviceId}
        />
      )}

      {activeTab === "relationships" && (
        <CardPlaceholder
          title="Relationship Graph"
          message="Relationship intelligence will be connected next."
        />
      )}

      {activeTab === "ai" && (
        <AIAnalysisPanel
          deviceId={deviceId}
        />
      )}

    </div>
  );
}

function OverviewPlaceholder() {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/30 p-8 text-center">
      <p className="text-slate-400">
        Device investigation overview.
      </p>
    </div>
  );
}

function CardPlaceholder({
  title,
  message
}: {
  title: string;
  message: string;
}) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/30 p-8 text-center">
      <h2 className="text-lg font-semibold text-white">
        {title}
      </h2>

      <p className="mt-2 text-slate-500">
        {message}
      </p>
    </div>
  );
}