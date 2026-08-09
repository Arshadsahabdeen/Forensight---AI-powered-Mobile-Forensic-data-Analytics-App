export interface TimelineEvent {
  id: number;
  artifactType: string;
  title: string | null;
  content: string | null;
  source: string | null;
  target: string | null;
  phoneNumber: string | null;
  contactName: string | null;
  location: string | null;
  latitude: number | null;
  longitude: number | null;
  eventTime: string | null;
  deviceId: number;
}