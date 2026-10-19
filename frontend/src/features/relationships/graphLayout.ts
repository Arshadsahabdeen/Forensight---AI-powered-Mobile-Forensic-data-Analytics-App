/** Deterministic force-directed layout (Fruchterman–Reingold). Same input always gives the same positions. */

export interface LayoutNode {
  id: string;
  group: string;
}

export interface LayoutEdge {
  source: string;
  target: string;
}

export interface Point {
  x: number;
  y: number;
}

export function layoutGraph(nodes: LayoutNode[], edges: LayoutEdge[], width = 1000, height = 700): Map<string, Point> {
  const positions = new Map<string, Point>();
  const n = nodes.length;
  if (n === 0) return positions;
  if (n === 1) {
    positions.set(nodes[0].id, { x: width / 2, y: height / 2 });
    return positions;
  }

  // Deterministic start: nodes on a spiral, ordered by group then id.
  const ordered = [...nodes].sort((a, b) => (a.group === b.group ? a.id.localeCompare(b.id) : a.group.localeCompare(b.group)));
  ordered.forEach((node, index) => {
    const angle = index * 2.399963; // golden angle
    const radius = 20 + 14 * Math.sqrt(index);
    positions.set(node.id, { x: width / 2 + radius * Math.cos(angle), y: height / 2 + radius * Math.sin(angle) });
  });

  const area = width * height;
  const k = Math.sqrt(area / n) * 0.75;
  const iterations = n > 250 ? 120 : n > 120 ? 180 : 260;
  let temperature = width / 8;
  const cooling = temperature / (iterations + 1);
  const ids = ordered.map((node) => node.id);
  const index = new Map(ids.map((id, i) => [id, i]));
  const validEdges = edges.filter((edge) => index.has(edge.source) && index.has(edge.target) && edge.source !== edge.target);
  const dx = new Float64Array(n);
  const dy = new Float64Array(n);
  const xs = new Float64Array(n);
  const ys = new Float64Array(n);
  ids.forEach((id, i) => {
    const p = positions.get(id)!;
    xs[i] = p.x;
    ys[i] = p.y;
  });

  for (let iteration = 0; iteration < iterations; iteration++) {
    dx.fill(0);
    dy.fill(0);
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        let ddx = xs[i] - xs[j];
        let ddy = ys[i] - ys[j];
        let distance = Math.sqrt(ddx * ddx + ddy * ddy);
        if (distance < 0.01) {
          ddx = 0.01 * ((i % 3) - 1 || 1);
          ddy = 0.01 * ((j % 3) - 1 || 1);
          distance = 0.015;
        }
        const force = (k * k) / distance;
        const fx = (ddx / distance) * force;
        const fy = (ddy / distance) * force;
        dx[i] += fx;
        dy[i] += fy;
        dx[j] -= fx;
        dy[j] -= fy;
      }
    }
    for (const edge of validEdges) {
      const a = index.get(edge.source)!;
      const b = index.get(edge.target)!;
      const ddx = xs[a] - xs[b];
      const ddy = ys[a] - ys[b];
      const distance = Math.max(0.01, Math.sqrt(ddx * ddx + ddy * ddy));
      const force = (distance * distance) / k;
      const fx = (ddx / distance) * force;
      const fy = (ddy / distance) * force;
      dx[a] -= fx;
      dy[a] -= fy;
      dx[b] += fx;
      dy[b] += fy;
    }
    for (let i = 0; i < n; i++) {
      // Mild gravity keeps disconnected components on screen.
      dx[i] += (width / 2 - xs[i]) * 0.02;
      dy[i] += (height / 2 - ys[i]) * 0.02;
      const displacement = Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i]);
      if (displacement > 0) {
        const limited = Math.min(displacement, temperature);
        xs[i] += (dx[i] / displacement) * limited;
        ys[i] += (dy[i] / displacement) * limited;
      }
      xs[i] = Math.min(width - 20, Math.max(20, xs[i]));
      ys[i] = Math.min(height - 20, Math.max(20, ys[i]));
    }
    temperature = Math.max(1, temperature - cooling);
  }

  ids.forEach((id, i) => positions.set(id, { x: xs[i], y: ys[i] }));
  return positions;
}

/** IDs of nodes within `hops` steps of `start`. */
export function neighbourhood(start: string, edges: LayoutEdge[], hops: number): Set<string> {
  const adjacency = new Map<string, string[]>();
  for (const edge of edges) {
    adjacency.set(edge.source, [...(adjacency.get(edge.source) ?? []), edge.target]);
    adjacency.set(edge.target, [...(adjacency.get(edge.target) ?? []), edge.source]);
  }
  const visited = new Set([start]);
  let frontier = [start];
  for (let step = 0; step < hops; step++) {
    const next: string[] = [];
    for (const id of frontier) {
      for (const neighbour of adjacency.get(id) ?? []) {
        if (!visited.has(neighbour)) {
          visited.add(neighbour);
          next.push(neighbour);
        }
      }
    }
    frontier = next;
  }
  return visited;
}
