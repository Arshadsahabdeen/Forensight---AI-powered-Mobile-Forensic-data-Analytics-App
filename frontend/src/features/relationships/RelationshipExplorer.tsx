import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Expand, GitBranch, List, Maximize2, Minus, Network, Plus, Search, X } from 'lucide-react';
import { getCorrelations } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button, IconButton } from '../../components/ui/Button';
import { Checkbox, Field, Input, Select } from '../../components/ui/Input';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Alert, EmptyState, ErrorState, Skeleton } from '../../components/ui/Feedback';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { BASIS_LABELS, formatUtc, humanize } from '../../lib/format';
import { EvidenceDrawer } from '../evidence/EvidenceDrawer';
import { layoutGraph, neighbourhood } from './graphLayout';
import type { CaseDetail, EntityType, GraphEntity, GraphRelationship } from '../../types/api';

const WIDTH = 1000;
const HEIGHT = 700;
const TYPES: EntityType[] = ['DEVICE', 'PHONE', 'PERSON', 'ACCOUNT', 'LOCATION', 'EVENT'];
const COLORS: Record<EntityType, string> = {
  DEVICE: '#334155',
  PHONE: '#1d4ed8',
  PERSON: '#047857',
  ACCOUNT: '#7c3aed',
  LOCATION: '#b45309',
  EVENT: '#94a3b8'
};

type Selection = { kind: 'node' | 'edge'; id: string } | null;

export function RelationshipExplorer({ caseId, devices }: { caseId: number; devices: Pick<CaseDetail['devices'][number], 'id' | 'deviceName'>[] }) {
  const [deviceId, setDeviceId] = useState('');
  const [includeEvents, setIncludeEvents] = useState(false);
  const [windowMinutes, setWindowMinutes] = useState(15);
  const [types, setTypes] = useState<Set<EntityType>>(new Set(TYPES));
  const [basis, setBasis] = useState<'ALL' | 'VERIFIED_RECORD' | 'INFERRED'>('ALL');
  const [search, setSearch] = useState('');
  const [focus, setFocus] = useState<string | null>(null);
  const [hops, setHops] = useState(1);
  const [selection, setSelection] = useState<Selection>(null);
  const [view, setView] = useState<'graph' | 'list'>('graph');
  const [evidenceId, setEvidenceId] = useState<number | null>(null);

  const graph = useQuery({
    queryKey: ['correlations', caseId, deviceId, includeEvents, windowMinutes],
    queryFn: () => getCorrelations(caseId, { deviceId: deviceId ? Number(deviceId) : undefined, includeEvents, windowMinutes })
  });

  const entityById = useMemo(() => new Map((graph.data?.entities ?? []).map((entity) => [entity.id, entity])), [graph.data]);

  const { nodes, edges } = useMemo(() => {
    const data = graph.data;
    if (!data) return { nodes: [] as GraphEntity[], edges: [] as GraphRelationship[] };
    let candidateNodes = data.entities.filter((entity) => types.has(entity.type));
    const nodeIds = new Set(candidateNodes.map((entity) => entity.id));
    let candidateEdges = data.relationships.filter((rel) => (basis === 'ALL' || rel.basis === basis) && nodeIds.has(rel.source) && nodeIds.has(rel.target));
    if (focus && nodeIds.has(focus)) {
      const visible = neighbourhood(focus, candidateEdges, hops);
      candidateNodes = candidateNodes.filter((entity) => visible.has(entity.id));
      candidateEdges = candidateEdges.filter((rel) => visible.has(rel.source) && visible.has(rel.target));
    }
    return { nodes: candidateNodes, edges: candidateEdges };
  }, [graph.data, types, basis, focus, hops]);

  const positions = useMemo(() => layoutGraph(nodes.map((n) => ({ id: n.id, group: n.type })), edges, WIDTH, HEIGHT), [nodes, edges]);
  const term = search.trim().toLowerCase();
  const matches = useMemo(
    () => (term ? nodes.filter((n) => n.label.toLowerCase().includes(term) || n.variants.some((v) => v.toLowerCase().includes(term))).map((n) => n.id) : []),
    [nodes, term]
  );

  const selectedNode = selection?.kind === 'node' ? entityById.get(selection.id) : undefined;
  const selectedEdge = selection?.kind === 'edge' ? graph.data?.relationships.find((rel) => rel.id === selection.id) : undefined;
  const connections = selectedNode ? edges.filter((rel) => rel.source === selectedNode.id || rel.target === selectedNode.id) : [];
  const highlighted = useMemo(() => {
    if (!selectedNode) return null;
    const ids = new Set([selectedNode.id]);
    connections.forEach((rel) => { ids.add(rel.source); ids.add(rel.target); });
    return ids;
  }, [selectedNode, connections]);

  function toggleType(type: EntityType) {
    setTypes((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  }

  function focusOn(id: string) {
    setFocus(id);
    setHops(1);
    setSelection({ kind: 'node', id });
  }

  return (
    <div className="space-y-4">
      <Card>
        <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-4">
          <Field label="Device">
            {(props) => (
              <Select {...props} value={deviceId} onChange={(event) => { setDeviceId(event.target.value); setFocus(null); setSelection(null); }}>
                <option value="">All devices (enables cross-device rules)</option>
                {devices.map((device) => <option key={device.id} value={device.id}>{device.deviceName}</option>)}
              </Select>
            )}
          </Field>
          <Field label="Relationship basis">
            {(props) => (
              <Select {...props} value={basis} onChange={(event) => setBasis(event.target.value as typeof basis)}>
                <option value="ALL">Stated and inferred</option>
                <option value="VERIFIED_RECORD">Stated directly by records</option>
                <option value="INFERRED">Inferred only</option>
              </Select>
            )}
          </Field>
          <Field label="Time-proximity window">
            {(props) => (
              <Select {...props} value={windowMinutes} onChange={(event) => setWindowMinutes(Number(event.target.value))}>
                <option value={0}>Off</option>
                <option value={5}>5 minutes</option>
                <option value={15}>15 minutes</option>
                <option value={60}>60 minutes</option>
              </Select>
            )}
          </Field>
          <Field label="Find entity">
            {(props) => (
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
                <Input {...props} className="pl-9" placeholder="Number, name, place…" value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  onKeyDown={(event) => { if (event.key === 'Enter' && matches[0]) focusOn(matches[0]); }} />
              </div>
            )}
          </Field>
        </div>
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-slate-200 px-4 py-3">
          {TYPES.map((type) => (
            <label key={type} className="flex items-center gap-1.5 text-sm text-slate-700">
              <input type="checkbox" className="h-4 w-4 rounded border-slate-300" checked={types.has(type)} onChange={() => toggleType(type)} />
              <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: COLORS[type] }} aria-hidden />
              {humanize(type)}
            </label>
          ))}
          <div className="ml-auto">
            <Checkbox label="Show individual events" checked={includeEvents} onChange={(event) => setIncludeEvents(event.target.checked)} />
          </div>
        </div>
      </Card>

      {graph.data && <Alert tone="info">{graph.data.disclaimer}</Alert>}
      {graph.data?.truncated && <Alert tone="warning">The graph was limited to the most connected entities. Filter by device or focus on an entity to see more.</Alert>}

      <div className="grid gap-4 xl:grid-cols-[1fr_380px]">
        <Card className="overflow-hidden">
          <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 px-4 py-2">
            <div className="inline-flex rounded-md border border-slate-300 p-0.5" role="group" aria-label="View">
              <button type="button" onClick={() => setView('graph')} aria-pressed={view === 'graph'}
                className={`inline-flex items-center gap-1 rounded px-2 py-1 text-xs ${view === 'graph' ? 'bg-slate-900 text-white' : 'text-slate-600'}`}><Network className="h-3.5 w-3.5" />Graph</button>
              <button type="button" onClick={() => setView('list')} aria-pressed={view === 'list'}
                className={`inline-flex items-center gap-1 rounded px-2 py-1 text-xs ${view === 'list' ? 'bg-slate-900 text-white' : 'text-slate-600'}`}><List className="h-3.5 w-3.5" />List</button>
            </div>
            <span className="text-xs text-slate-500">{nodes.length} entities · {edges.length} relationships{graph.data ? ` · ${graph.data.evidenceConsidered} records analysed` : ''}</span>
            {focus && (
              <span className="ml-auto flex items-center gap-2">
                <Badge tone="brand">Focused: {entityById.get(focus)?.label} · {hops} hop(s)</Badge>
                <Button size="sm" variant="secondary" icon={<Expand className="h-3.5 w-3.5" />} onClick={() => setHops((value) => Math.min(value + 1, 6))}>Expand</Button>
                <Button size="sm" variant="ghost" icon={<X className="h-3.5 w-3.5" />} onClick={() => setFocus(null)}>Show all</Button>
              </span>
            )}
          </div>
          {graph.isLoading ? (
            <Skeleton className="m-4 h-[480px]" />
          ) : graph.isError ? (
            <ErrorState message={friendlyError(graph.error)} onRetry={() => void graph.refetch()} />
          ) : nodes.length === 0 ? (
            <EmptyState icon={<GitBranch className="h-6 w-6" />} title="No relationships to show"
              description={graph.data?.evidenceConsidered ? 'No entities match the current filters.' : 'This case has no evidence records yet. Import an extraction file first.'} />
          ) : view === 'graph' ? (
            <GraphCanvas nodes={nodes} edges={edges} positions={positions} matches={matches} highlighted={highlighted}
              selection={selection} onSelect={setSelection} onFocus={focusOn} />
          ) : (
            <RelationshipTable edges={edges} entityById={entityById} onSelect={setSelection} />
          )}
          <Legend />
        </Card>

        <Card>
          <CardHeader title={selectedNode ? selectedNode.label : selectedEdge ? humanize(selectedEdge.type) : 'Details'}
            description={selectedNode ? humanize(selectedNode.type) : selectedEdge ? BASIS_LABELS[selectedEdge.basis] : 'Select an entity or a relationship.'} />
          <CardBody className="max-h-[640px] space-y-4 overflow-y-auto">
            {!selectedNode && !selectedEdge && (
              <div className="space-y-3 text-sm text-slate-600">
                <p>Click an entity to see its connections and supporting records. Double-click to focus on its neighbourhood.</p>
                <details className="rounded-md border border-slate-200 p-3">
                  <summary className="cursor-pointer text-sm font-medium text-slate-800">How relationships are derived</summary>
                  <ul className="mt-2 space-y-2">
                    {graph.data?.rules.map((rule) => (
                      <li key={rule.code}><StatusBadge value={rule.basis} label={BASIS_LABELS[rule.basis]} /> <span className="text-xs">{rule.description}</span></li>
                    ))}
                  </ul>
                </details>
              </div>
            )}
            {selectedNode && (
              <>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div><dt className="text-xs text-slate-500">Records</dt><dd>{selectedNode.evidenceCount}</dd></div>
                  <div><dt className="text-xs text-slate-500">Devices</dt><dd>{selectedNode.deviceIds.length}</dd></div>
                  <div><dt className="text-xs text-slate-500">First seen</dt><dd className="text-xs">{formatUtc(selectedNode.firstSeen)}</dd></div>
                  <div><dt className="text-xs text-slate-500">Last seen</dt><dd className="text-xs">{formatUtc(selectedNode.lastSeen)}</dd></div>
                </dl>
                {selectedNode.variants.length > 1 && (
                  <div><p className="text-xs text-slate-500">Values seen</p><p className="mono mt-0.5 break-words">{selectedNode.variants.join(' · ')}</p></div>
                )}
                {selectedNode.note && <Alert tone="warning">{selectedNode.note}</Alert>}
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => focusOn(selectedNode.id)}>Focus</Button>
                  {selectedNode.type !== 'DEVICE' && selectedNode.type !== 'EVENT' && (
                    <Link className="link self-center text-sm" to={`/evidence?caseId=${caseId}&search=${encodeURIComponent(selectedNode.variants[0] ?? selectedNode.label)}`}>Search evidence</Link>
                  )}
                </div>
                <div>
                  <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Connections ({connections.length})</h3>
                  <ul className="space-y-3">
                    {[...connections].sort((a, b) => b.recordCount - a.recordCount).map((rel) => {
                      const otherId = rel.source === selectedNode.id ? rel.target : rel.source;
                      return (
                        <li key={rel.id} className="rounded-md border border-slate-200 p-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <button type="button" className="link text-sm" onClick={() => setSelection({ kind: 'node', id: otherId })}>{entityById.get(otherId)?.label ?? otherId}</button>
                            <StatusBadge value={rel.basis} label={BASIS_LABELS[rel.basis]} />
                          </div>
                          <p className="mt-1 text-xs text-slate-500">{humanize(rel.type)}</p>
                          <p className="mt-1 text-xs text-slate-700">{rel.explanation}</p>
                          <EvidenceChips ids={rel.evidenceIds} truncated={rel.evidenceTruncated} total={rel.recordCount} onOpen={setEvidenceId} />
                        </li>
                      );
                    })}
                  </ul>
                </div>
              </>
            )}
            {selectedEdge && (
              <>
                <p className="text-sm text-slate-700">{selectedEdge.explanation}</p>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div><dt className="text-xs text-slate-500">Between</dt><dd>{entityById.get(selectedEdge.source)?.label} and {entityById.get(selectedEdge.target)?.label}</dd></div>
                  <div><dt className="text-xs text-slate-500">Rule</dt><dd className="mono">{selectedEdge.rule}</dd></div>
                  <div><dt className="text-xs text-slate-500">First</dt><dd className="text-xs">{formatUtc(selectedEdge.firstSeen)}</dd></div>
                  <div><dt className="text-xs text-slate-500">Last</dt><dd className="text-xs">{formatUtc(selectedEdge.lastSeen)}</dd></div>
                </dl>
                <EvidenceChips ids={selectedEdge.evidenceIds} truncated={selectedEdge.evidenceTruncated} total={selectedEdge.recordCount} onOpen={setEvidenceId} />
              </>
            )}
          </CardBody>
        </Card>
      </div>
      <EvidenceDrawer evidenceId={evidenceId} onClose={() => setEvidenceId(null)} onSelect={setEvidenceId} />
    </div>
  );
}

function EvidenceChips({ ids, truncated, total, onOpen }: { ids: number[]; truncated: boolean; total: number; onOpen: (id: number) => void }) {
  const [all, setAll] = useState(false);
  const shown = all ? ids : ids.slice(0, 12);
  return (
    <div className="mt-2">
      <p className="mb-1 text-xs text-slate-500">Supporting records ({total})</p>
      <div className="flex flex-wrap gap-1">
        {shown.map((id) => (
          <button key={id} type="button" onClick={() => onOpen(id)} className="mono rounded border border-slate-200 px-1.5 py-0.5 text-brand-700 hover:bg-brand-50">#{id}</button>
        ))}
        {!all && ids.length > shown.length && <button type="button" className="text-xs text-slate-500 underline" onClick={() => setAll(true)}>+{ids.length - shown.length} more</button>}
      </div>
      {truncated && <p className="mt-1 text-xs text-slate-500">Only the first {ids.length} record IDs are listed.</p>}
    </div>
  );
}

function GraphCanvas({ nodes, edges, positions, matches, highlighted, selection, onSelect, onFocus }: {
  nodes: GraphEntity[];
  edges: GraphRelationship[];
  positions: Map<string, { x: number; y: number }>;
  matches: string[];
  highlighted: Set<string> | null;
  selection: Selection;
  onSelect: (selection: Selection) => void;
  onFocus: (id: string) => void;
}) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [transform, setTransform] = useState({ x: 0, y: 0, k: 1 });
  const drag = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null);
  const matchSet = useMemo(() => new Set(matches), [matches]);
  const showAllLabels = nodes.length <= 40 || transform.k >= 1.6;

  useEffect(() => setTransform({ x: 0, y: 0, k: 1 }), [nodes.length]);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const factor = event.deltaY < 0 ? 1.15 : 1 / 1.15;
      setTransform((current) => ({ ...current, k: Math.min(6, Math.max(0.3, current.k * factor)) }));
    };
    svg.addEventListener('wheel', onWheel, { passive: false });
    return () => svg.removeEventListener('wheel', onWheel);
  }, []);

  function onPointerDown(event: ReactPointerEvent<SVGSVGElement>) {
    if ((event.target as Element).closest('[data-node],[data-edge]')) return;
    drag.current = { x: event.clientX, y: event.clientY, ox: transform.x, oy: transform.y };
    (event.currentTarget as SVGSVGElement).setPointerCapture(event.pointerId);
  }
  function onPointerMove(event: ReactPointerEvent<SVGSVGElement>) {
    if (!drag.current || !svgRef.current) return;
    const scale = WIDTH / svgRef.current.clientWidth;
    setTransform((current) => ({ ...current, x: drag.current!.ox + (event.clientX - drag.current!.x) * scale, y: drag.current!.oy + (event.clientY - drag.current!.y) * scale }));
  }
  function onPointerUp() {
    drag.current = null;
  }

  const dim = (id: string) => highlighted !== null && !highlighted.has(id);

  return (
    <div className="relative">
      <div className="absolute right-3 top-3 z-[1] flex flex-col gap-1 rounded-md border border-slate-200 bg-white p-1 shadow-card">
        <IconButton label="Zoom in" onClick={() => setTransform((t) => ({ ...t, k: Math.min(6, t.k * 1.25) }))}><Plus className="h-4 w-4" /></IconButton>
        <IconButton label="Zoom out" onClick={() => setTransform((t) => ({ ...t, k: Math.max(0.3, t.k / 1.25) }))}><Minus className="h-4 w-4" /></IconButton>
        <IconButton label="Reset view" onClick={() => setTransform({ x: 0, y: 0, k: 1 })}><Maximize2 className="h-4 w-4" /></IconButton>
      </div>
      <svg ref={svgRef} viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="h-[520px] w-full cursor-grab touch-none bg-slate-50 active:cursor-grabbing"
        role="img" aria-label="Relationship graph. Use the list view for an accessible table."
        onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerLeave={onPointerUp}
        onClick={(event) => { if (!(event.target as Element).closest('[data-node],[data-edge]')) onSelect(null); }}>
        <g transform={`translate(${WIDTH / 2 + transform.x} ${HEIGHT / 2 + transform.y}) scale(${transform.k}) translate(${-WIDTH / 2} ${-HEIGHT / 2})`}>
          {edges.map((rel) => {
            const a = positions.get(rel.source);
            const b = positions.get(rel.target);
            if (!a || !b) return null;
            const selected = selection?.kind === 'edge' && selection.id === rel.id;
            const faded = highlighted !== null && !(highlighted.has(rel.source) && highlighted.has(rel.target));
            return (
              <g key={rel.id} data-edge onClick={(event) => { event.stopPropagation(); onSelect({ kind: 'edge', id: rel.id }); }} className="cursor-pointer">
                <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke="transparent" strokeWidth={10 / transform.k} />
                <line x1={a.x} y1={a.y} x2={b.x} y2={b.y}
                  stroke={selected ? '#1d4ed8' : rel.basis === 'INFERRED' ? '#d97706' : '#64748b'}
                  strokeOpacity={faded ? 0.12 : 0.75}
                  strokeWidth={(selected ? 2.5 : Math.min(4, 0.8 + Math.log2(rel.recordCount + 1) * 0.6)) / transform.k}
                  strokeDasharray={rel.basis === 'INFERRED' ? `${6 / transform.k} ${4 / transform.k}` : undefined} />
              </g>
            );
          })}
          {nodes.map((node) => {
            const p = positions.get(node.id);
            if (!p) return null;
            const selected = selection?.kind === 'node' && selection.id === node.id;
            const matched = matchSet.has(node.id);
            const radius = (node.type === 'EVENT' ? 4 : 6 + Math.min(8, Math.sqrt(node.evidenceCount))) / Math.sqrt(transform.k);
            const opacity = dim(node.id) ? 0.2 : 1;
            const label = showAllLabels || selected || matched || node.type === 'DEVICE' || (highlighted?.has(node.id) ?? false);
            return (
              <g key={node.id} data-node transform={`translate(${p.x} ${p.y})`} opacity={opacity} className="cursor-pointer"
                onClick={(event) => { event.stopPropagation(); onSelect({ kind: 'node', id: node.id }); }}
                onDoubleClick={(event) => { event.stopPropagation(); onFocus(node.id); }}>
                <title>{`${humanize(node.type)}: ${node.label} (${node.evidenceCount} records)`}</title>
                {(selected || matched) && <circle r={radius + 5 / transform.k} fill="none" stroke={matched && !selected ? '#f59e0b' : '#1d4ed8'} strokeWidth={2 / transform.k} />}
                {node.type === 'DEVICE' ? (
                  <rect x={-radius} y={-radius} width={radius * 2} height={radius * 2} rx={2} fill={COLORS[node.type]} />
                ) : node.type === 'LOCATION' ? (
                  <rect x={-radius * 0.8} y={-radius * 0.8} width={radius * 1.6} height={radius * 1.6} fill={COLORS[node.type]} transform="rotate(45)" />
                ) : (
                  <circle r={radius} fill={COLORS[node.type]} />
                )}
                {label && (
                  <text y={radius + 12 / transform.k} textAnchor="middle" fontSize={11 / transform.k} fill="#0f172a" stroke="#f8fafc" strokeWidth={3 / transform.k} paintOrder="stroke">
                    {node.label.length > 28 ? `${node.label.slice(0, 27)}…` : node.label}
                  </text>
                )}
              </g>
            );
          })}
        </g>
      </svg>
    </div>
  );
}

function RelationshipTable({ edges, entityById, onSelect }: { edges: GraphRelationship[]; entityById: Map<string, GraphEntity>; onSelect: (s: Selection) => void }) {
  return (
    <Table minWidth={720}>
      <thead>
        <tr><Th>From</Th><Th>Relationship</Th><Th>To</Th><Th>Basis</Th><Th className="text-right">Records</Th></tr>
      </thead>
      <tbody>
        {[...edges].sort((a, b) => b.recordCount - a.recordCount).map((rel) => (
          <Tr key={rel.id} onClick={() => onSelect({ kind: 'edge', id: rel.id })}>
            <Td>{entityById.get(rel.source)?.label}</Td>
            <Td><span className="text-xs">{humanize(rel.type)}</span></Td>
            <Td>{entityById.get(rel.target)?.label}</Td>
            <Td><StatusBadge value={rel.basis} label={BASIS_LABELS[rel.basis]} /></Td>
            <Td className="text-right tabular-nums">{rel.recordCount}</Td>
          </Tr>
        ))}
      </tbody>
    </Table>
  );
}

function Legend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-slate-200 px-4 py-2 text-xs text-slate-600">
      <span className="flex items-center gap-1"><svg width="22" height="6" aria-hidden><line x1="0" y1="3" x2="22" y2="3" stroke="#64748b" strokeWidth="2" /></svg>Stated by records</span>
      <span className="flex items-center gap-1"><svg width="22" height="6" aria-hidden><line x1="0" y1="3" x2="22" y2="3" stroke="#d97706" strokeWidth="2" strokeDasharray="5 3" /></svg>Inferred</span>
      <span>■ device · ◆ location · ● identifier / person · line weight = number of records</span>
    </div>
  );
}
