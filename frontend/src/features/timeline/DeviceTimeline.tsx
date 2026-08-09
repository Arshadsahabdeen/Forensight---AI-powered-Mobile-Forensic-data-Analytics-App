import { useQuery } from "@tanstack/react-query";
import {
  MessageSquare,
  Phone,
  MapPin,
  User,
  Image,
  Video,
  Globe,
  Clock,
  Loader2
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { getDeviceTimeline } from "../../services/investigationService";

interface Props {
  deviceId: number;
}

function iconForType(type: string) {

  switch (type.toUpperCase()) {

    case "SMS":
      return <MessageSquare size={18} />;

    case "CALL":
      return <Phone size={18} />;

    case "LOCATION":
      return <MapPin size={18} />;

    case "CONTACT":
      return <User size={18} />;

    case "PHOTO":
      return <Image size={18} />;

    case "VIDEO":
      return <Video size={18} />;

    case "BROWSER":
    case "BROWSER_HISTORY":
      return <Globe size={18} />;

    default:
      return <Clock size={18} />;
  }
}

export function DeviceTimeline({
  deviceId
}: Props) {

  const {
    data,
    isLoading,
    isError
  } = useQuery({
    queryKey: ["device-timeline", deviceId],
    queryFn: () => getDeviceTimeline(deviceId)
  });

  if (isLoading) {
    return (
      <Card>
        <div className="p-10 text-center text-slate-400">
          <Loader2
            className="mx-auto mb-3 animate-spin text-cyan-400"
          />

          Loading forensic timeline...
        </div>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card>
        <div className="p-8 text-center text-red-400">
          Failed to load device timeline.
        </div>
      </Card>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Card>
        <div className="p-8 text-center text-slate-500">
          No timeline events found for this device.
        </div>
      </Card>
    );
  }

  return (
    <Card>

      <div className="mb-6">

        <h2 className="text-xl font-semibold text-white">
          Forensic Timeline
        </h2>

        <p className="mt-1 text-sm text-slate-400">
          Chronological sequence of extracted forensic events.
        </p>

      </div>

      <div className="relative">

        <div className="absolute left-5 top-0 bottom-0 w-px bg-slate-800" />

        <div className="space-y-6">

          {data.map((event) => (

            <div
              key={event.id}
              className="relative flex gap-4"
            >

              <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-slate-700 bg-slate-950 text-cyan-400">
                {iconForType(event.artifactType)}
              </div>

              <div className="min-w-0 flex-1 rounded-lg border border-slate-800 bg-slate-900/50 p-4">

                <div className="flex flex-wrap items-center justify-between gap-2">

                  <span className="rounded-md bg-cyan-500/10 px-2 py-1 text-xs font-medium text-cyan-300">
                    {event.artifactType}
                  </span>

                  {event.eventTime && (
                    <span className="text-xs text-slate-500">
                      {new Date(
                        event.eventTime
                      ).toLocaleString()}
                    </span>
                  )}

                </div>

                {event.title && (
                  <h3 className="mt-3 font-medium text-white">
                    {event.title}
                  </h3>
                )}

                {event.content && (
                  <p className="mt-2 text-sm text-slate-300">
                    {event.content}
                  </p>
                )}

                {(event.source || event.target) && (
                  <div className="mt-3 text-xs text-slate-500">

                    {event.source && (
                      <span>
                        From: {event.source}
                      </span>
                    )}

                    {event.target && (
                      <span className="ml-4">
                        To: {event.target}
                      </span>
                    )}

                  </div>
                )}

                {event.phoneNumber && (
                  <p className="mt-2 text-xs text-slate-400">
                    Phone: {event.phoneNumber}
                  </p>
                )}

                {event.contactName && (
                  <p className="mt-2 text-xs text-slate-400">
                    Contact: {event.contactName}
                  </p>
                )}

                {event.location && (
                  <p className="mt-2 text-xs text-slate-400">
                    Location: {event.location}
                  </p>
                )}

              </div>

            </div>

          ))}

        </div>

      </div>

    </Card>
  );
}