import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  ArrowRight,
  Calendar,
  Clock,
  Database,
  Globe,
  Image,
  MapPin,
  MessageSquare,
  Phone,
  Search,
  User,
  Video,
  X,
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { getTimeline } from "../../services/investigationService";
import type { TimelineEvent } from "../../types/timeline";

type TimelineFilter =
  | "ALL"
  | "MESSAGES"
  | "LOCATIONS"
  | "CALLS"
  | "MEDIA"
  | "BROWSER";

const filters: {
  key: TimelineFilter;
  label: string;
}[] = [
  { key: "ALL", label: "Overview" },
  { key: "MESSAGES", label: "Messages" },
  { key: "LOCATIONS", label: "Locations" },
  { key: "CALLS", label: "Calls" },
  { key: "MEDIA", label: "Media" },
  { key: "BROWSER", label: "Browser" },
];

function filterEvent(
  event: TimelineEvent,
  filter: TimelineFilter
) {
  const type = event.artifactType.toUpperCase();

  switch (filter) {
    case "MESSAGES":
      return type === "SMS" || type === "MESSAGE";

    case "LOCATIONS":
      return type === "LOCATION";

    case "CALLS":
      return type === "CALL";

    case "MEDIA":
      return type === "PHOTO" || type === "VIDEO";

    case "BROWSER":
      return (
        type === "BROWSER" ||
        type === "BROWSER_HISTORY"
      );

    default:
      return true;
  }
}

function iconForType(type: string) {
  switch (type.toUpperCase()) {
    case "SMS":
    case "MESSAGE":
      return <MessageSquare size={17} />;

    case "CALL":
      return <Phone size={17} />;

    case "LOCATION":
      return <MapPin size={17} />;

    case "CONTACT":
      return <User size={17} />;

    case "PHOTO":
      return <Image size={17} />;

    case "VIDEO":
      return <Video size={17} />;

    case "BROWSER":
    case "BROWSER_HISTORY":
      return <Globe size={17} />;

    default:
      return <Database size={17} />;
  }
}

function formatDate(value: string | null) {
  if (!value) {
    return "Unknown";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(date);
}

function formatTime(value: string | null) {
  if (!value) {
    return "Unknown";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
}

function formatFullDate(value: string | null) {
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

function eventImportance(event: TimelineEvent) {
  const type = event.artifactType.toUpperCase();

  if (
    type === "LOCATION" ||
    type === "CALL"
  ) {
    return "HIGH";
  }

  if (
    type === "SMS" ||
    type === "MESSAGE"
  ) {
    return "MEDIUM";
  }

  return "LOW";
}

function importanceClasses(level: string) {
  switch (level) {
    case "HIGH":
      return "bg-red-500/10 text-red-300 border-red-500/20";

    case "MEDIUM":
      return "bg-yellow-500/10 text-yellow-300 border-yellow-500/20";

    default:
      return "bg-slate-800 text-slate-400 border-slate-700";
  }
}

function dateKey(event: TimelineEvent) {
  if (!event.eventTime) {
    return "Unknown";
  }

  const date = new Date(event.eventTime);

  if (Number.isNaN(date.getTime())) {
    return "Unknown";
  }

  return date.toISOString().slice(0, 10);
}

export function TimelinePage() {
  const {
    data = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ["timeline"],
    queryFn: getTimeline,
  });

  const [filter, setFilter] =
    useState<TimelineFilter>("ALL");

  const [selectedDate, setSelectedDate] =
    useState<string | null>(null);

  const [selectedEvent, setSelectedEvent] =
    useState<TimelineEvent | null>(null);

  const [visibleCount, setVisibleCount] =
    useState(80);

  const sortedEvents = useMemo(() => {
    return [...data].sort((a, b) => {
      if (!a.eventTime && !b.eventTime) {
        return a.id - b.id;
      }

      if (!a.eventTime) {
        return 1;
      }

      if (!b.eventTime) {
        return -1;
      }

      return (
        new Date(a.eventTime).getTime() -
        new Date(b.eventTime).getTime()
      );
    });
  }, [data]);

  const availableDates = useMemo(() => {
    const dates = new Set<string>();

    sortedEvents.forEach((event) => {
      if (event.eventTime) {
        dates.add(dateKey(event));
      }
    });

    return Array.from(dates).sort();
  }, [sortedEvents]);

  const filteredEvents = useMemo(() => {
    return sortedEvents.filter((event) => {
      if (!filterEvent(event, filter)) {
        return false;
      }

      if (
        selectedDate &&
        dateKey(event) !== selectedDate
      ) {
        return false;
      }

      return true;
    });
  }, [
    sortedEvents,
    filter,
    selectedDate,
  ]);

  const visibleEvents = filteredEvents.slice(
    0,
    visibleCount
  );

  const importantEvents = useMemo(() => {
    return filteredEvents
      .filter(
        (event) =>
          eventImportance(event) === "HIGH"
      )
      .slice(0, 8);
  }, [filteredEvents]);

  const density = useMemo(() => {
    const map = new Map<
      string,
      number
    >();

    sortedEvents.forEach((event) => {
      if (!event.eventTime) {
        return;
      }

      const key = dateKey(event);

      map.set(
        key,
        (map.get(key) ?? 0) + 1
      );
    });

    return Array.from(map.entries());
  }, [sortedEvents]);

  const maxDensity = Math.max(
    ...density.map(([, count]) => count),
    1
  );

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-3xl font-bold text-white">
          Investigation Timeline
        </h1>

        <Card>
          <div className="p-10 text-center text-slate-400">
            Loading forensic timeline...
          </div>
        </Card>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="space-y-6">
        <h1 className="text-3xl font-bold text-white">
          Investigation Timeline
        </h1>

        <Card>
          <div className="p-10 text-center">
            <p className="text-red-400">
              Failed to load timeline.
            </p>

            <p className="mt-2 text-sm text-slate-500">
              {error instanceof Error
                ? error.message
                : "Unable to retrieve timeline data."}
            </p>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}

      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="flex items-center gap-2 text-cyan-400">
            <Activity size={20} />

            <span className="text-sm font-medium">
              Forensic Investigation
            </span>
          </div>

          <h1 className="mt-1 text-3xl font-bold text-white">
            Investigation Timeline
          </h1>

          <p className="mt-2 text-slate-400">
            Interactive reconstruction of extracted
            forensic activity.
          </p>
        </div>

        <div className="rounded-lg border border-slate-800 bg-slate-900 px-4 py-3">
          <p className="text-xs text-slate-500">
            Total Events
          </p>

          <p className="mt-1 text-2xl font-bold text-cyan-400">
            {data.length.toLocaleString()}
          </p>
        </div>
      </div>

      {/* Filter Tabs */}

      <Card>
        <div className="flex flex-wrap gap-2">
          {filters.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => {
                setFilter(item.key);
                setVisibleCount(80);
              }}
              className={`rounded-lg px-4 py-2.5 text-sm font-medium transition ${
                filter === item.key
                  ? "bg-cyan-500/15 text-cyan-300 ring-1 ring-cyan-500/30"
                  : "bg-slate-900 text-slate-400 hover:text-white"
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </Card>

      {/* Event Density */}

      {density.length > 0 && (
        <Card>
          <div className="mb-5 flex items-center justify-between">
            <div>
              <div className="flex items-center gap-2">
                <Calendar
                  size={17}
                  className="text-cyan-400"
                />

                <h2 className="font-semibold text-white">
                  Activity Density
                </h2>
              </div>

              <p className="mt-1 text-xs text-slate-500">
                Click a day to focus the investigation.
              </p>
            </div>

            {selectedDate && (
              <button
                type="button"
                onClick={() =>
                  setSelectedDate(null)
                }
                className="text-xs text-cyan-400 hover:text-cyan-300"
              >
                Clear date
              </button>
            )}
          </div>

          <div className="overflow-x-auto">
            <div className="flex min-w-[700px] items-end gap-2">
              {density.map(
                ([date, count]) => {
                  const height =
                    Math.max(
                      12,
                      (count /
                        maxDensity) *
                        120
                    );

                  return (
                    <button
                      key={date}
                      type="button"
                      onClick={() => {
                        setSelectedDate(
                          selectedDate === date
                            ? null
                            : date
                        );

                        setVisibleCount(80);
                      }}
                      className="group flex min-w-[42px] flex-1 flex-col items-center justify-end"
                    >
                      <span className="mb-1 text-[10px] text-slate-500 opacity-0 transition group-hover:opacity-100">
                        {count}
                      </span>

                      <div
                        className={`w-full max-w-[34px] rounded-t-md transition ${
                          selectedDate === date
                            ? "bg-cyan-400"
                            : "bg-cyan-500/30 group-hover:bg-cyan-500/60"
                        }`}
                        style={{
                          height: `${height}px`,
                        }}
                      />

                      <span className="mt-2 whitespace-nowrap text-[10px] text-slate-500">
                        {formatDate(
                          date
                        )}
                      </span>
                    </button>
                  );
                }
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Important Events */}

      {importantEvents.length > 0 && (
        <Card>
          <div className="mb-4">
            <h2 className="font-semibold text-white">
              Important Events
            </h2>

            <p className="mt-1 text-xs text-slate-500">
              High-value events surfaced from the
              selected evidence.
            </p>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            {importantEvents.map(
              (event) => (
                <button
                  key={event.id}
                  type="button"
                  onClick={() =>
                    setSelectedEvent(event)
                  }
                  className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 text-left transition hover:border-cyan-500/30 hover:bg-slate-900"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <div className="text-cyan-400">
                        {iconForType(
                          event.artifactType
                        )}
                      </div>

                      <span className="text-sm font-medium text-white">
                        {event.artifactType}
                      </span>
                    </div>

                    <span
                      className={`rounded-full border px-2 py-1 text-[10px] font-medium ${importanceClasses(
                        eventImportance(
                          event
                        )
                      )}`}
                    >
                      HIGH
                    </span>
                  </div>

                  {event.title && (
                    <p className="mt-3 truncate text-sm text-slate-200">
                      {event.title}
                    </p>
                  )}

                  {event.content && (
                    <p className="mt-1 line-clamp-2 text-xs text-slate-500">
                      {event.content}
                    </p>
                  )}

                  <p className="mt-3 text-xs text-slate-600">
                    {formatFullDate(
                      event.eventTime
                    )}
                  </p>
                </button>
              )
            )}
          </div>
        </Card>
      )}

      {/* Compact Timeline */}

      <Card>
        <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="font-semibold text-white">
              Event Timeline
            </h2>

            <p className="mt-1 text-xs text-slate-500">
              {filteredEvents.length.toLocaleString()} matching
              events
            </p>
          </div>

          {selectedDate && (
            <span className="rounded-md bg-cyan-500/10 px-3 py-1.5 text-xs text-cyan-300">
              {formatDate(selectedDate)}
            </span>
          )}
        </div>

        {visibleEvents.length === 0 ? (
          <div className="p-10 text-center text-slate-500">
            No events match the selected filters.
          </div>
        ) : (
          <div className="relative overflow-x-auto pb-4">
            <div className="relative min-w-[900px] px-6">
              {/* Main horizontal line */}

              <div className="absolute left-0 right-0 top-[48px] h-px bg-slate-800" />

              <div className="flex items-start gap-3">
                {visibleEvents.map(
                  (event) => (
                    <button
                      key={event.id}
                      type="button"
                      onClick={() =>
                        setSelectedEvent(
                          event
                        )
                      }
                      className="group relative w-[115px] shrink-0 text-center"
                    >
                      <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full border border-slate-700 bg-slate-950 text-cyan-400 transition group-hover:border-cyan-500 group-hover:bg-cyan-500/10">
                        {iconForType(
                          event.artifactType
                        )}
                      </div>

                      <p className="mt-3 truncate text-[11px] font-medium text-slate-300">
                        {event.artifactType}
                      </p>

                      <p className="mt-1 text-[10px] text-slate-600">
                        {formatTime(
                          event.eventTime
                        )}
                      </p>
                    </button>
                  )
                )}
              </div>
            </div>
          </div>
        )}

        {visibleCount <
          filteredEvents.length && (
          <div className="mt-6 text-center">
            <button
              type="button"
              onClick={() =>
                setVisibleCount(
                  (value) => value + 80
                )
              }
              className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-900 px-5 py-2.5 text-sm text-slate-300 transition hover:border-cyan-500/40 hover:text-white"
            >
              Show more events
              <ArrowRight size={15} />
            </button>
          </div>
        )}
      </Card>

      {/* Event Detail Drawer */}

      {selectedEvent && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50">
          <div className="h-full w-full max-w-lg overflow-y-auto border-l border-slate-800 bg-slate-950 p-6 shadow-2xl">
            <div className="flex items-start justify-between">
              <div>
                <span className="inline-flex items-center gap-2 rounded-md bg-cyan-500/10 px-3 py-1.5 text-xs font-medium text-cyan-300">
                  {iconForType(
                    selectedEvent.artifactType
                  )}

                  {selectedEvent.artifactType}
                </span>

                <h2 className="mt-3 text-xl font-semibold text-white">
                  Evidence Details
                </h2>
              </div>

              <button
                type="button"
                onClick={() =>
                  setSelectedEvent(null)
                }
                className="rounded-lg p-2 text-slate-500 hover:bg-slate-900 hover:text-white"
              >
                <X size={20} />
              </button>
            </div>

            <div className="mt-6 space-y-5">
              <div>
                <p className="text-xs uppercase text-slate-600">
                  Event Time
                </p>

                <p className="mt-1 text-sm text-slate-300">
                  {formatFullDate(
                    selectedEvent.eventTime
                  )}
                </p>
              </div>

              {selectedEvent.title && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Title
                  </p>

                  <p className="mt-1 text-sm text-white">
                    {selectedEvent.title}
                  </p>
                </div>
              )}

              {selectedEvent.content && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Content
                  </p>

                  <div className="mt-2 rounded-lg border border-slate-800 bg-slate-900 p-4 text-sm leading-6 text-slate-300">
                    {selectedEvent.content}
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4">
                {selectedEvent.source && (
                  <div>
                    <p className="text-xs uppercase text-slate-600">
                      Source
                    </p>

                    <p className="mt-1 break-all text-sm text-slate-300">
                      {selectedEvent.source}
                    </p>
                  </div>
                )}

                {selectedEvent.target && (
                  <div>
                    <p className="text-xs uppercase text-slate-600">
                      Target
                    </p>

                    <p className="mt-1 break-all text-sm text-slate-300">
                      {selectedEvent.target}
                    </p>
                  </div>
                )}
              </div>

              {selectedEvent.phoneNumber && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Phone Number
                  </p>

                  <p className="mt-1 text-sm text-slate-300">
                    {selectedEvent.phoneNumber}
                  </p>
                </div>
              )}

              {selectedEvent.contactName && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Contact
                  </p>

                  <p className="mt-1 text-sm text-slate-300">
                    {selectedEvent.contactName}
                  </p>
                </div>
              )}

              {selectedEvent.location && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Location
                  </p>

                  <p className="mt-1 flex items-center gap-2 text-sm text-slate-300">
                    <MapPin
                      size={15}
                      className="text-cyan-400"
                    />

                    {selectedEvent.location}
                  </p>
                </div>
              )}

              {(
                selectedEvent.latitude !== null ||
                selectedEvent.longitude !== null
              ) && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Coordinates
                  </p>

                  <p className="mt-1 text-sm text-slate-300">
                    {selectedEvent.latitude ?? "-"},{" "}
                    {selectedEvent.longitude ?? "-"}
                  </p>
                </div>
              )}

              <div>
                <p className="text-xs uppercase text-slate-600">
                  Device
                </p>

                <p className="mt-1 text-sm text-cyan-400">
                  {selectedEvent.deviceId !==
                  null
                    ? `Device #${selectedEvent.deviceId}`
                    : "Unknown"}
                </p>
              </div>

              <div>
                <p className="text-xs uppercase text-slate-600">
                  Evidence ID
                </p>

                <p className="mt-1 text-sm text-slate-500">
                  #{selectedEvent.id}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}