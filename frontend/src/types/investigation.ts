export interface InvestigationNode {
  id: string;
  label: string;
  type: string;
}

export interface InvestigationEdge {
  source: string;
  target: string;
  relationship: string;
  evidenceId: number;
}

export interface RelationshipGraphResponse {
  nodes: InvestigationNode[];
  edges: InvestigationEdge[];
}
