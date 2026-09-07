import { useEffect } from "react";
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from "react-leaflet";
import { LatLngBounds } from "leaflet";
import "leaflet/dist/leaflet.css";

export interface EvidenceMapEvent {
  id: number;
  latitude: number;
  longitude: number;
  location: string | null;
  title?: string | null;
  artifactType?: string | null;
  eventTime?: string | null;
}

export function LeafletEvidenceMap({ events }: { events: EvidenceMapEvent[] }) {
  if (!events.length) return <div className="flex h-72 items-center justify-center rounded-lg border border-slate-700 bg-slate-950 text-sm text-slate-500">No GPS coordinates were found in this device&apos;s evidence.</div>;

  const center: [number, number] = [events[0].latitude, events[0].longitude];
  return <div className="h-72 overflow-hidden rounded-lg border border-slate-700"><MapContainer center={center} zoom={12} scrollWheelZoom className="h-full w-full" aria-label="Evidence locations on OpenStreetMap"><TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/><FitEvidenceBounds events={events}/>{events.map(event => <CircleMarker key={event.id} center={[event.latitude, event.longitude]} radius={8} pathOptions={{ color: "#082f49", weight: 2, fillColor: "#22d3ee", fillOpacity: 0.9 }}><Popup><div className="min-w-48 text-sm text-slate-900"><strong>{event.title || event.artifactType || "Evidence location"}</strong>{event.location && <div>{event.location}</div>}{event.eventTime && <div>{new Date(event.eventTime).toLocaleString("en-IN")}</div>}<div>{event.latitude.toFixed(6)}, {event.longitude.toFixed(6)}</div></div></Popup></CircleMarker>)}</MapContainer></div>;
}

function FitEvidenceBounds({ events }: { events: EvidenceMapEvent[] }) {
  const map = useMap();

  useEffect(() => {
    const bounds = new LatLngBounds(events.map(event => [event.latitude, event.longitude] as [number, number]));
    if (events.length === 1) {
      map.setView(bounds.getCenter(), 14);
    } else {
      map.fitBounds(bounds, { padding: [32, 32] });
    }
  }, [events, map]);

  return null;
}