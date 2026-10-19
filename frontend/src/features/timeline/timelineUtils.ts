import type { TimelineEvent } from '../../types/api';

/** Groups chronologically ordered events by their UTC calendar day, preserving order. */
export function groupByUtcDay(events: TimelineEvent[]): { day: string; events: TimelineEvent[] }[] {
  const groups: { day: string; events: TimelineEvent[] }[] = [];
  for (const event of events) {
    const day = event.eventTime ? event.eventTime.slice(0, 10) : 'undated';
    const last = groups[groups.length - 1];
    if (last && last.day === day) last.events.push(event);
    else groups.push({ day, events: [event] });
  }
  return groups;
}

export function utcTimeOfDay(iso: string | null): string {
  if (!iso) return '—';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toISOString().slice(11, 19);
}

export function formatDayHeading(day: string): string {
  if (day === 'undated') return 'Undated';
  const date = new Date(`${day}T00:00:00Z`);
  return new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: '2-digit', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(date);
}
