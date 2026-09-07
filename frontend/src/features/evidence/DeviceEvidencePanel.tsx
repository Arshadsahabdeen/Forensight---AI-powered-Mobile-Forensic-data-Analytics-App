import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Database,
  Filter,
  MapPin,
  MessageSquare,
  Phone,
  RotateCcw,
  User,
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import { getDeviceEvidence } from "../../services/evidenceService";
import { friendlyError } from "../../services/errors";

interface DeviceEvidencePanelProps {
  deviceId: number;
}

type ArtifactFilter =
  | "ALL"
  | "SMS"
  | "CALL"
  | "CONTACT"
  | "LOCATION";

const filters: ArtifactFilter[] = [
  "ALL",
  "SMS",
  "CALL",
  "CONTACT",
  "LOCATION",
];

function iconForArtifact(type: string | null) {
  switch (type?.toUpperCase()) {
    case "SMS":
      return <MessageSquare size={16} />;

    case "CALL":
      return <Phone size={16} />;

    case "CONTACT":
      return <User size={16} />;

    case "LOCATION":
      return <MapPin size={16} />;

    default:
      return <Database size={16} />;
  }
}

function formatTimestamp(timestamp: string | null) {
  if (!timestamp) {
    return "Time unavailable";
  }

  const date = new Date(timestamp);

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

export function DeviceEvidencePanel({
  deviceId,
}: DeviceEvidencePanelProps) {
  const [artifactFilter, setArtifactFilter] =
    useState<ArtifactFilter>("ALL");

  const {
    data,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["device-evidence", deviceId, artifactFilter],
    queryFn: () => getDeviceEvidence(deviceId, {
      page: 0,
      size: 50,
      sort: "eventTime",
      direction: "desc",
      artifactType: artifactFilter === "ALL" ? undefined : artifactFilter,
    }),
    enabled: !!deviceId,
  });

  const filteredEvidence = data?.items ?? [];

  if (isLoading) {
    return (
      <Card>
        <div className="p-8 text-center text-slate-400">
          Loading extracted evidence...
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
              "Unable to load device evidence."
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

  if (!data || data.totalElements === 0) {
    return (
      <Card>
        <div className="p-8 text-center text-slate-400">
          No evidence has been extracted for this device yet.
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <div className="space-y-5">
        {/* Header */}

        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="flex items-center gap-2 text-cyan-400">
              <Database size={18} />

              <span className="text-sm font-medium">
                Evidence
              </span>
            </div>

            <h2 className="mt-1 text-xl font-semibold text-white">
              Extracted Device Artifacts
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              {data.totalElements} real forensic artifacts extracted
              from this device.
            </p>
          </div>

          {/* Filters */}

          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-2 rounded-md border border-slate-800 bg-slate-900 px-3 py-2 text-xs text-slate-400">
              <Filter size={14} />
              Filter
            </span>

            {filters.map((filter) => (
              <button
                key={filter}
                type="button"
                onClick={() =>
                  setArtifactFilter(filter)
                }
                className={`rounded-md px-3 py-2 text-xs font-medium transition ${
                  artifactFilter === filter
                    ? "bg-cyan-500/15 text-cyan-200"
                    : "bg-slate-900 text-slate-400 hover:text-white"
                }`}
              >
                {filter}
              </button>
            ))}
          </div>
        </div>

        {/* Filter result count */}

        <div className="text-xs text-slate-500">
          Showing {filteredEvidence.length} of {data.totalElements} artifacts
        </div>

        {/* Evidence */}

        {filteredEvidence.length === 0 ? (
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 text-sm text-slate-400">
            No artifacts match the selected filter.
          </div>
        ) : (
          <div className="space-y-3">
            {filteredEvidence.map((event) => (
              <div
                key={event.id}
                className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 transition hover:border-slate-700"
              >
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-700 bg-slate-950 text-cyan-300">
                      {iconForArtifact(
                        event.artifactType
                      )}
                    </div>

                    <div>
                      <div className="text-sm font-semibold text-white">
                        {event.artifactType}
                      </div>

                      <div className="text-xs text-slate-500">
                        {formatTimestamp(
                          event.eventTime
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="text-xs text-slate-500">
                    Evidence ID: {event.id}
                  </div>
                </div>

                {/* Title */}

                {event.title && (
                  <p className="mt-3 text-sm font-medium text-slate-200">
                    {event.title}
                  </p>
                )}

                {/* Content */}

                {event.content && (
                  <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-slate-300">
                    {event.content}
                  </p>
                )}

                {/* Metadata */}

                <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-xs text-slate-400">
                  {event.source && (
                    <span>
                      Source: {event.source}
                    </span>
                  )}

                  {event.target && (
                    <span>
                      Target: {event.target}
                    </span>
                  )}

                  {event.phoneNumber && (
                    <span>
                      Phone: {event.phoneNumber}
                    </span>
                  )}

                  {event.contactName && (
                    <span>
                      Contact: {event.contactName}
                    </span>
                  )}

                  {event.location && (
                    <span>
                      Location: {event.location}
                    </span>
                  )}

                  {event.latitude !== null &&
                    event.longitude !== null && (
                      <span>
                        Coordinates:{" "}
                        {event.latitude.toFixed(5)},{" "}
                        {event.longitude.toFixed(5)}
                      </span>
                    )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}
