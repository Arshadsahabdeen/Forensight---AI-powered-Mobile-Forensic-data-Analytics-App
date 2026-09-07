import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  GitBranch,
  Network,
  Phone,
  RotateCcw,
  User
} from "lucide-react";

import { Card } from "../../../components/ui/Card";
import { Button } from "../../../components/ui/Button";
import { friendlyError } from "../../../services/errors";
import { getDeviceRelationshipGraph } from "../../../services/investigationService";

interface DeviceRelationshipsPanelProps {
  deviceId: number;
}

function iconForNodeType(type: string) {
  return type === "PERSON" ? <User size={16} /> : <Phone size={16} />;
}

export function DeviceRelationshipsPanel({
  deviceId
}: DeviceRelationshipsPanelProps) {
  const {
    data,
    isLoading,
    isError,
    error,
    refetch
  } = useQuery({
    queryKey: ["device-relationship-graph", deviceId],
    queryFn: () => getDeviceRelationshipGraph(deviceId)
  });

  const nodeLabels = useMemo(() => {
    const labels = new Map<string, string>();
    for (const node of data?.nodes ?? []) {
      labels.set(node.id, node.label);
    }
    return labels;
  }, [data]);

  if (isLoading) {
    return (
      <Card>
        <div className="p-10 text-center text-slate-400">
          Loading relationship graph...
        </div>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card>
        <div className="p-8 text-center">
          <p className="text-sm text-rose-300">
            {friendlyError(error, "Unable to load relationship graph.")}
          </p>

          <Button className="mt-4" onClick={() => refetch()}>
            <RotateCcw size={16} />
            Retry
          </Button>
        </div>
      </Card>
    );
  }

  if (!data || (data.nodes.length === 0 && data.edges.length === 0)) {
    return (
      <Card>
        <div className="p-8 text-center text-slate-400">
          No relationship data is available for this device.
        </div>
      </Card>
    );
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
      <Card className="bg-slate-950/70">
        <div className="mb-5 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-cyan-500/20 bg-cyan-500/10 text-cyan-300">
            <Network size={18} />
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
              Relationships
            </p>
            <h2 className="text-xl font-semibold text-white">
              Connected Entities
            </h2>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          {data.nodes.map((node) => (
            <div
              key={node.id}
              className="rounded-lg border border-slate-800 bg-slate-900/60 p-4"
            >
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-700 bg-slate-950 text-cyan-300">
                  {iconForNodeType(node.type)}
                </div>

                <div>
                  <div className="text-sm font-semibold text-white">
                    {node.label}
                  </div>
                  <div className="text-xs text-slate-500">
                    {node.type}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card className="bg-slate-950/70">
        <div className="mb-5 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-slate-800 bg-slate-900 text-cyan-300">
            <GitBranch size={18} />
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
              Links
            </p>
            <h2 className="text-xl font-semibold text-white">
              Evidence Relationships
            </h2>
          </div>
        </div>

        <div className="space-y-3">
          {data.edges.length === 0 ? (
            <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-4 text-sm text-slate-400">
              No relationships were derived from the available evidence.
            </div>
          ) : (
            data.edges.map((edge) => (
              <div
                key={`${edge.evidenceId}-${edge.source}-${edge.target}-${edge.relationship}`}
                className="rounded-lg border border-slate-800 bg-slate-900/60 p-4"
              >
                <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                  <div className="text-sm font-medium text-white">
                    {nodeLabels.get(edge.source) ?? edge.source}
                    <span className="mx-2 text-cyan-300">-&gt;</span>
                    {nodeLabels.get(edge.target) ?? edge.target}
                  </div>

                  <div className="rounded-full bg-cyan-500/10 px-3 py-1 text-xs font-medium text-cyan-200">
                    {edge.relationship}
                  </div>
                </div>

                <div className="mt-2 text-xs text-slate-500">
                  Source: {edge.source} | Target: {edge.target} | Evidence ID: {edge.evidenceId}
                </div>
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}
