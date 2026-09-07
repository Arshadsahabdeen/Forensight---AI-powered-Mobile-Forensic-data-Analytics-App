import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Calendar,
  Clock,
  Database,
  Globe,
  Image,
  MapPin,
  MessageSquare,
  Phone,
  User,
  Video,
  X,
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { getDeviceTimeline } from "../../services/investigationService";
import type { TimelineEvent } from "../../types/timeline";

interface DeviceTimelineProps {
  deviceId: number;
}

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
  { key: "ALL", label: "All" },
  { key: "MESSAGES", label: "Messages" },
  { key: "LOCATIONS", label: "Locations" },
  { key: "CALLS", label: "Calls" },
  { key: "MEDIA", label: "Media" },
  { key: "BROWSER", label: "Browser" },
];

function matchesFilter(
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

export function DeviceTimeline({
  deviceId,
}: DeviceTimelineProps) {
  const [filter, setFilter] =
    useState<TimelineFilter>("ALL");

  const [selectedDate, setSelectedDate] =
    useState<string | null>(null);

  const [selectedEvent, setSelectedEvent] =
    useState<TimelineEvent | null>(null);

  const [visibleCount, setVisibleCount] =
    useState(60);

  const {
    data = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["device-timeline", deviceId],
    queryFn: () =>
      getDeviceTimeline(deviceId),
    enabled: !!deviceId,
  });

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
    return Array.from(
      new Set(
        sortedEvents
          .filter(
            (event) => event.eventTime
          )
          .map(dateKey)
      )
    ).sort();
  }, [sortedEvents]);

  const filteredEvents = useMemo(() => {
    return sortedEvents.filter((event) => {
      if (!matchesFilter(event, filter)) {
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

  const visibleEvents =
    filteredEvents.slice(
      0,
      visibleCount
    );

  const locations = sortedEvents.filter(
    (event) =>
      event.artifactType.toUpperCase() ===
      "LOCATION"
  );

  const messages = sortedEvents.filter(
    (event) => {
      const type =
        event.artifactType.toUpperCase();

      return (
        type === "SMS" ||
        type === "MESSAGE"
      );
    }
  );

  if (isLoading) {
    return (
      <Card>
        <div className="p-8 text-center text-slate-400">
          Loading forensic timeline...
        </div>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card>
        <div className="p-8 text-center">
          <p className="text-red-400">
            Failed to load device timeline.
          </p>

          <p className="mt-2 text-sm text-slate-500">
            Refresh the investigation workspace
            and try again.
          </p>
        </div>
      </Card>
    );
  }

  if (data.length === 0) {
    return (
      <Card>
        <div className="p-8 text-center">
          <Database
            size={32}
            className="mx-auto text-slate-600"
          />

          <p className="mt-4 font-medium text-slate-300">
            No timeline events found
          </p>

          <p className="mt-2 text-sm text-slate-500">
            Extracted artifacts will appear
            here once timeline data is available.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <>
      <Card>
        <div className="space-y-6">
          {/* Header */}

          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-cyan-300">
                Device Investigation
              </p>

              <h2 className="mt-1 text-xl font-semibold text-white">
                Interactive Forensic Timeline
              </h2>

              <p className="mt-1 text-sm text-slate-400">
                Explore messages, locations,
                calls and other device activity.
              </p>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div className="rounded-lg border border-slate-800 bg-slate-900 px-3 py-2">
                <p className="text-[10px] uppercase text-slate-600">
                  Events
                </p>

                <p className="text-lg font-bold text-white">
                  {data.length}
                </p>
              </div>

              <div className="rounded-lg border border-slate-800 bg-slate-900 px-3 py-2">
                <p className="text-[10px] uppercase text-slate-600">
                  Messages
                </p>

                <p className="text-lg font-bold text-cyan-400">
                  {messages.length}
                </p>
              </div>

              <div className="rounded-lg border border-slate-800 bg-slate-900 px-3 py-2">
                <p className="text-[10px] uppercase text-slate-600">
                  Locations
                </p>

                <p className="text-lg font-bold text-purple-400">
                  {locations.length}
                </p>
              </div>
            </div>
          </div>

          {/* Filters */}

          <div className="flex flex-wrap gap-2">
            {filters.map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => {
                  setFilter(item.key);
                  setVisibleCount(60);
                }}
                className={`rounded-lg px-3 py-2 text-xs font-medium transition ${
                  filter === item.key
                    ? "bg-cyan-500/15 text-cyan-300 ring-1 ring-cyan-500/30"
                    : "bg-slate-900 text-slate-400 hover:text-white"
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>

          {/* Date selector */}

          {availableDates.length > 0 && (
            <div>
              <div className="mb-3 flex items-center gap-2">
                <Calendar
                  size={15}
                  className="text-cyan-400"
                />

                <span className="text-xs text-slate-500">
                  Focus by date
                </span>
              </div>

              <div className="flex gap-2 overflow-x-auto pb-2">
                {availableDates.map(
                  (date) => (
                    <button
                      key={date}
                      type="button"
                      onClick={() => {
                        setSelectedDate(
                          selectedDate === date
                            ? null
                            : date
                        );

                        setVisibleCount(
                          60
                        );
                      }}
                      className={`whitespace-nowrap rounded-md border px-3 py-2 text-xs ${
                        selectedDate === date
                          ? "border-cyan-500/40 bg-cyan-500/10 text-cyan-300"
                          : "border-slate-800 bg-slate-900 text-slate-500 hover:text-white"
                      }`}
                    >
                      {formatDate(date)}
                    </button>
                  )
                )}
              </div>
            </div>
          )}

          {/* Horizontal timeline */}

          <div>
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h3 className="text-sm font-semibold text-white">
                  Event Stream
                </h3>

                <p className="mt-1 text-xs text-slate-500">
                  Click any event for details.
                </p>
              </div>

              <span className="text-xs text-slate-600">
                Showing{" "}
                {visibleEvents.length}{" "}
                /{" "}
                {filteredEvents.length}
              </span>
            </div>

            <div className="overflow-x-auto pb-5">
              <div className="relative min-w-[900px] px-6 pt-2">
                <div className="absolute left-0 right-0 top-7 h-px bg-slate-800" />

                <div className="flex gap-4">
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
                        className="group relative w-[105px] shrink-0 text-center"
                      >
                        <div className="relative z-10 mx-auto flex h-10 w-10 items-center justify-center rounded-full border border-slate-700 bg-slate-950 text-cyan-400 transition group-hover:border-cyan-400 group-hover:bg-cyan-500/10">
                          {iconForType(
                            event.artifactType
                          )}
                        </div>

                        <p className="mt-3 truncate text-[10px] font-semibold text-slate-400">
                          {
                            event.artifactType
                          }
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
          </div>

          {/* More */}

          {visibleCount <
            filteredEvents.length && (
            <div className="text-center">
              <button
                type="button"
                onClick={() =>
                  setVisibleCount(
                    (value) =>
                      value + 60
                  )
                }
                className="rounded-lg border border-slate-700 bg-slate-900 px-5 py-2 text-xs text-slate-300 hover:border-cyan-500/40 hover:text-white"
              >
                Show more events
              </button>
            </div>
          )}

          {/* Location journey */}

          {locations.length > 0 &&
            (filter === "ALL" ||
              filter ===
                "LOCATIONS") && (
              <div className="border-t border-slate-800 pt-6">
                <div className="mb-4">
                  <div className="flex items-center gap-2">
                    <MapPin
                      size={17}
                      className="text-purple-400"
                    />

                    <h3 className="font-semibold text-white">
                      Location Journey
                    </h3>
                  </div>

                  <p className="mt-1 text-xs text-slate-500">
                    Chronological movement extracted
                    from device location artifacts.
                  </p>
                </div>

                <div className="overflow-x-auto">
                  <div className="flex min-w-max items-center gap-0">
                    {locations
                      .slice(0, 30)
                      .map(
                        (
                          event,
                          index
                        ) => (
                          <div
                            key={
                              event.id
                            }
                            className="flex items-center"
                          >
                            <button
                              type="button"
                              onClick={() =>
                                setSelectedEvent(
                                  event
                                )
                              }
                              className="group w-[170px] rounded-lg border border-slate-800 bg-slate-900/60 p-3 text-left hover:border-purple-500/30"
                            >
                              <div className="flex items-center gap-2">
                                <MapPin
                                  size={15}
                                  className="text-purple-400"
                                />

                                <span className="text-xs font-medium text-white">
                                  {event.location ??
                                    "Unknown location"}
                                </span>
                              </div>

                              <p className="mt-2 text-[10px] text-slate-600">
                                {formatFullDate(
                                  event.eventTime
                                )}
                              </p>

                              {event.latitude !==
                                null &&
                                event.longitude !==
                                  null && (
                                  <p className="mt-2 text-[10px] text-slate-500">
                                    {
                                      event.latitude
                                    }
                                    ,{" "}
                                    {
                                      event.longitude
                                    }
                                  </p>
                                )}
                            </button>

                            {index <
                              Math.min(
                                locations.length,
                                30
                              ) -
                                1 && (
                              <div className="h-px w-8 bg-purple-500/30" />
                            )}
                          </div>
                        )
                      )}
                  </div>
                </div>
              </div>
            )}
        </div>
      </Card>

      {/* Event detail drawer */}

      {selectedEvent && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50">
          <div className="h-full w-full max-w-lg overflow-y-auto border-l border-slate-800 bg-slate-950 p-6 shadow-2xl">
            <div className="flex items-start justify-between">
              <div>
                <span className="inline-flex items-center gap-2 rounded-md bg-cyan-500/10 px-3 py-1.5 text-xs font-medium text-cyan-300">
                  {iconForType(
                    selectedEvent.artifactType
                  )}

                  {
                    selectedEvent.artifactType
                  }
                </span>

                <h2 className="mt-3 text-xl font-semibold text-white">
                  Event Details
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
                    {
                      selectedEvent.title
                    }
                  </p>
                </div>
              )}

              {selectedEvent.content && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Content
                  </p>

                  <div className="mt-2 rounded-lg border border-slate-800 bg-slate-900 p-4 text-sm leading-6 text-slate-300">
                    {
                      selectedEvent.content
                    }
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
                      {
                        selectedEvent.source
                      }
                    </p>
                  </div>
                )}

                {selectedEvent.target && (
                  <div>
                    <p className="text-xs uppercase text-slate-600">
                      Target
                    </p>

                    <p className="mt-1 break-all text-sm text-slate-300">
                      {
                        selectedEvent.target
                      }
                    </p>
                  </div>
                )}
              </div>

              {selectedEvent.phoneNumber && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Phone
                  </p>

                  <p className="mt-1 text-sm text-slate-300">
                    {
                      selectedEvent.phoneNumber
                    }
                  </p>
                </div>
              )}

              {selectedEvent.contactName && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Contact
                  </p>

                  <p className="mt-1 text-sm text-slate-300">
                    {
                      selectedEvent.contactName
                    }
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
                      className="text-purple-400"
                    />

                    {
                      selectedEvent.location
                    }
                  </p>
                </div>
              )}

              {(
                selectedEvent.latitude !==
                  null ||
                selectedEvent.longitude !==
                  null
              ) && (
                <div>
                  <p className="text-xs uppercase text-slate-600">
                    Coordinates
                  </p>

                  <p className="mt-1 text-sm text-slate-300">
                    {
                      selectedEvent.latitude ??
                        "-"
                    }
                    ,{" "}
                    {
                      selectedEvent.longitude ??
                        "-"
                    }
                  </p>
                </div>
              )}

              <div>
                <p className="text-xs uppercase text-slate-600">
                  Device
                </p>

                <p className="mt-1 text-sm text-cyan-400">
                  Device #{deviceId}
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
    </>
  );
}