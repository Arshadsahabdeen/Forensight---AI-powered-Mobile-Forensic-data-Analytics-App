import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { formatBytes, formatUtc, humanize, shortHash, utcInputToIso } from '../lib/format';
import { validateCase } from '../features/cases/caseValidation';
import { validateDevice } from '../features/devices/deviceOptions';
import { validateUpload } from '../features/files/EvidenceFilesPanel';
import { parseIds } from '../features/workspace/FindingDialog';
import { layoutGraph, neighbourhood } from '../features/relationships/graphLayout';
import { groupByUtcDay, utcTimeOfDay } from '../features/timeline/timelineUtils';
import { fieldErrors, friendlyError } from '../services/errors';
import type { CaseRequest, DeviceRequest, TimelineEvent } from '../types/api';

describe('format helpers', () => {
  it('formats instants in UTC regardless of the browser timezone', () => {
    expect(formatUtc('2026-07-14T03:42:00Z')).toBe('14 Jul 2026, 03:42:00 UTC');
    expect(formatUtc(null)).toBe('—');
  });

  it('treats datetime-local filter values as UTC', () => {
    expect(utcInputToIso('2026-07-14T09:20')).toBe('2026-07-14T09:20:00.000Z');
    expect(utcInputToIso('')).toBeUndefined();
  });

  it('humanizes enums, sizes and hashes', () => {
    expect(humanize('WAITING_FOR_EXTRACTION')).toBe('Waiting for extraction');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(shortHash('a'.repeat(64))).toBe(`${'a'.repeat(12)}…`);
  });
});

describe('form validation', () => {
  const validCase: CaseRequest = {
    caseNumber: 'CR-2026/0042', title: 'Title', description: 'Description', status: 'OPEN', priority: 'HIGH',
    leadInvestigator: 'Aarav Sharma', category: 'Cybercrime', caseType: 'Mobile Forensics'
  };

  it('accepts a valid case and rejects unsafe case numbers and missing fields', () => {
    expect(validateCase(validCase)).toEqual({});
    expect(validateCase({ ...validCase, caseNumber: '../etc' }).caseNumber).toBeDefined();
    expect(validateCase({ ...validCase, title: ' ', leadInvestigator: '' })).toMatchObject({ title: expect.any(String), leadInvestigator: expect.any(String) });
  });

  it('validates device IMEI format and date order', () => {
    const device: DeviceRequest = {
      caseId: 1, manufacturer: 'Google', model: 'Pixel 8', deviceName: 'Handset', operatingSystem: 'Android',
      acquisitionMethod: 'LOGICAL_EXTRACTION', acquisitionStatus: 'SEIZED', imei: ''
    };
    expect(validateDevice(device)).toEqual({});
    expect(validateDevice({ ...device, imei: '12345' }).imei).toBeDefined();
    expect(validateDevice({ ...device, seizedDate: '2026-07-10', receivedDate: '2026-07-01' }).receivedDate).toBeDefined();
  });

  it('pre-validates uploads by extension, emptiness and size', () => {
    expect(validateUpload(null)).toMatch(/Choose a file/);
    expect(validateUpload(new File(['x'], 'notes.txt'))).toMatch(/Only \.xml/);
    expect(validateUpload(new File([], 'empty.xml'))).toMatch(/empty/);
    expect(validateUpload(new File(['<UFDR/>'], 'phone.XML'))).toBeNull();
  });

  it('parses evidence ID lists and rejects invalid entries', () => {
    expect(parseIds('12, #15 15;18')).toEqual([12, 15, 18]);
    expect(parseIds('')).toEqual([]);
    expect(parseIds('12, abc')).toBeNull();
    expect(parseIds('0')).toBeNull();
  });
});

describe('graph layout', () => {
  const nodes = [
    { id: 'device:1', group: 'DEVICE' },
    { id: 'phone:1', group: 'PHONE' },
    { id: 'phone:2', group: 'PHONE' },
    { id: 'person:a', group: 'PERSON' }
  ];
  const edges = [
    { source: 'device:1', target: 'phone:1' },
    { source: 'phone:1', target: 'phone:2' },
    { source: 'person:a', target: 'phone:1' }
  ];

  it('is deterministic and stays inside the canvas', () => {
    const first = layoutGraph(nodes, edges, 1000, 700);
    const second = layoutGraph(nodes, edges, 1000, 700);
    expect([...first.entries()]).toEqual([...second.entries()]);
    for (const point of first.values()) {
      expect(point.x).toBeGreaterThanOrEqual(20);
      expect(point.x).toBeLessThanOrEqual(980);
      expect(point.y).toBeGreaterThanOrEqual(20);
      expect(point.y).toBeLessThanOrEqual(680);
    }
  });

  it('expands neighbourhoods one hop at a time', () => {
    expect([...neighbourhood('phone:2', edges, 1)].sort()).toEqual(['phone:1', 'phone:2']);
    expect([...neighbourhood('phone:2', edges, 2)].sort()).toEqual(['device:1', 'person:a', 'phone:1', 'phone:2']);
  });
});

describe('timeline grouping', () => {
  const event = (id: number, time: string | null) => ({ evidenceId: id, eventTime: time } as TimelineEvent);

  it('groups ordered events by UTC day', () => {
    const groups = groupByUtcDay([event(1, '2026-07-14T23:59:00Z'), event(2, '2026-07-15T00:01:00Z'), event(3, '2026-07-15T08:00:00Z')]);
    expect(groups.map((g) => [g.day, g.events.length])).toEqual([['2026-07-14', 1], ['2026-07-15', 2]]);
    expect(utcTimeOfDay('2026-07-15T08:00:00Z')).toBe('08:00:00');
  });
});

describe('API error messages', () => {
  function axiosError(status: number, data: unknown) {
    const headers = new AxiosHeaders();
    return new AxiosError('failed', 'ERR', { headers }, null, { status, statusText: '', headers, config: { headers }, data });
  }

  it('prefers the server message and exposes field errors', () => {
    const error = axiosError(400, { message: 'Validation failed', fields: { title: 'must not be blank' } });
    expect(friendlyError(error)).toBe('Validation failed');
    expect(fieldErrors(error)).toEqual({ title: 'must not be blank' });
  });

  it('falls back to status-specific messages', () => {
    expect(friendlyError(axiosError(403, {}))).toMatch(/permission/);
    expect(friendlyError(axiosError(503, {}))).toMatch(/maintenance/);
    expect(friendlyError(new AxiosError('Network Error'))).toMatch(/Unable to reach the server/);
  });
});
