import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Database,
  Filter,
  Globe,
  Image,
  Loader2,
  MapPin,
  MessageSquare,
  RotateCcw,
  UserCircle2,
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import { friendlyError } from "../../services/errors";
import {
  getNormalizedEvidenceByDevice,
  getNormalizedEvidenceByDeviceAndCategory,
} from "../../services/normalizedEvidenceService";
import type {
  NormalizedEvidence,
  NormalizedEvidenceCategory,
} from "../../types/normalizedEvidence";

interface NormalizedEvidencePanelProps {
  deviceId: number;
}

type EvidenceFilter =
  | "ALL"
  | "COMMUNICATION"
  | "LOCATION"
  | "CONTACT"
  | "MEDIA"
  | "BROWSER";

const filters: {
  value: EvidenceFilter;
  label: string;
}[] = [
  { value: "ALL", label: "All" },
  { value: "COMMUNICATION", label: "Communication" },
  { value: "LOCATION", label: "Location" },
  { value: "CONTACT", label: "Contact" },
  { value: "MEDIA", label: "Media" },
  { value: "BROWSER", label: "Browser" },
];

function formatDate(value: string | null | undefined) {
  if (!value) {
    return "Time unavailable";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Time unavailable";
  }

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
}

function iconForCategory(category: NormalizedEvidenceCategory) {
  switch (category) {
    case "COMMUNICATION":
      return <MessageSquare size={16} />;
    case "LOCATION":
      return <MapPin size={16} />;
    case "CONTACT":
    case "ACCOUNT":
      return <UserCircle2 size={16} />;
    case "MEDIA":
      return <Image size={16} />;
    case "BROWSER":
      return <Globe size={16} />;
    default:
      return <Database size={16} />;
  }
}

function displayValue(value: unknown) {
  if (value === null || value === undefined) {
    return null;
  }

  if (Array.isArray(value)) {
    return value.join(", ");
  }

  if (typeof value === "object") {
    return JSON.stringify(value);
  }

  const stringValue = String(value).trim();
  return stringValue ? stringValue : null;
}

function topMetadataEntries(metadata: Record<string, unknown>) {
  return Object.entries(metadata)
    .map(([key, value]) => ({
      key,
      value: displayValue(value),
    }))
    .filter(
      (entry): entry is { key: string; value: string } =>
        entry.value !== null
    )
    .slice(0, 5);
}

export function NormalizedEvidencePanel({
  deviceId,
}: NormalizedEvidencePanelProps) {
  const [filter, setFilter] =
    useState<EvidenceFilter>("ALL");

  const {
    data = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: [
      "normalized-evidence",
      deviceId,
      filter,
    ],
    queryFn: () =>
      filter === "ALL"
        ? getNormalizedEvidenceByDevice(deviceId)
        : getNormalizedEvidenceByDeviceAndCategory(
            deviceId,
            filter
          ),
    enabled: Number.isFinite(deviceId),
  });

  if (isLoading) {
    return (
      <Card>
        <div className="flex items-center justify-center gap-3 p-8 text-slate-400">
          <Loader2
            size={18}
            className="animate-spin"
          />
          Loading normalized evidence...
        </div>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card>
        <div className="p-8 text-center">
          <p className="text-red-400">
            {friendlyError(
              error,
              "Unable to load normalized evidence."
            )}
          </p>

          <Button
            className="mt-4"
            onClick={() => refetch()}
          >
            <RotateCcw size={16} />
            Retry
          </Button>
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <div className="space-y-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="flex items-center gap-2 text-cyan-400">
              <Database size={18} />
              <span className="text-sm font-medium">
                Normalized Evidence
              </span>
            </div>

            <h2 className="mt-1 text-xl font-semibold text-white">
              Standardized Investigation Artifacts
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Canonical evidence records prepared for
              timeline, AI analysis, and relationship
              intelligence.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-2 rounded-md border border-slate-800 bg-slate-900 px-3 py-2 text-xs text-slate-400">
              <Filter size={14} />
              Filter
            </span>

            {filters.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() =>
                  setFilter(item.value)
                }
                className={`rounded-md px-3 py-2 text-xs font-medium transition ${
                  filter === item.value
                    ? "bg-cyan-500/15 text-cyan-200"
                    : "bg-slate-900 text-slate-400 hover:text-white"
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>

        <div className="text-xs text-slate-500">
          Showing {data.length} normalized artifact
          {data.length === 1 ? "" : "s"}
        </div>

        {data.length === 0 ? (
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 text-sm text-slate-400">
            No normalized evidence is available for
            this filter yet.
          </div>
        ) : (
          <div className="space-y-3">
            {data.map((item) => (
              <NormalizedEvidenceCard
                key={item.id}
                item={item}
              />
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}

function NormalizedEvidenceCard({
  item,
}: {
  item: NormalizedEvidence;
}) {
  const metadataItems = topMetadataEntries(
    item.metadata
  );

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 transition hover:border-slate-700">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-700 bg-slate-950 text-cyan-300">
            {iconForCategory(item.category)}
          </div>

          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold text-white">
                {item.title ||
                  item.artifactType}
              </span>

              <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-cyan-200">
                {item.category}
              </span>
            </div>

            <div className="mt-1 text-xs text-slate-500">
              {item.artifactType} | {item.sourceTool} |{" "}
              {formatDate(
                item.eventTime || item.createdAt
              )}
            </div>
          </div>
        </div>

        <div className="text-xs text-slate-500">
          ID {item.id}
        </div>
      </div>

      {item.content && (
        <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-300">
          {item.content}
        </p>
      )}

      {metadataItems.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-xs text-slate-400">
          {metadataItems.map((entry) => (
            <span key={entry.key}>
              {entry.key}: {entry.value}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
