import dagre from '@dagrejs/dagre';
import type { FsmEditorDocument, Point } from './types';

export function autoLayout(index: number, total: number): Point {
  const radius = Math.max(220, total * 34);
  const angle = total <= 1 ? 0 : (2 * Math.PI * index) / total - Math.PI / 2;

  return {
    x: Math.round(Math.cos(angle) * radius),
    y: Math.round(Math.sin(angle) * radius),
  };
}

export interface NodeSize {
  width: number;
  height: number;
}

/** Size of a state node before React Flow has measured it. */
const DEFAULT_NODE_SIZE: NodeSize = { width: 160, height: 44 };

/**
 * Top-down layered positions (top-left corners) for every state. The initial state goes first so
 * cycle breaking keeps it on the top layer and edges back to it point upwards.
 */
export function verticalLayout(
  document: Pick<FsmEditorDocument, 'states' | 'transitions' | 'codegen'>,
  sizes: ReadonlyMap<string, NodeSize> = new Map(),
): Map<string, Point> {
  const graph = new dagre.graphlib.Graph({ multigraph: true });
  graph.setGraph({ rankdir: 'TB', nodesep: 60, ranksep: 90, edgesep: 30, marginx: 20, marginy: 20 });
  const initial = document.states.find((state) => state.label === document.codegen.initialState);
  const states = initial ? [initial, ...document.states.filter((state) => state !== initial)] : document.states;
  states.forEach((state) => graph.setNode(state.id, { ...(sizes.get(state.id) ?? DEFAULT_NODE_SIZE) }));
  document.transitions.forEach((transition) => {
    if (graph.hasNode(transition.from) && graph.hasNode(transition.to)) {
      graph.setEdge(transition.from, transition.to, {}, transition.id);
    }
  });
  dagre.layout(graph);
  return new Map(states.map((state) => {
    const node = graph.node(state.id);
    return [state.id, { x: Math.round(node.x - node.width / 2), y: Math.round(node.y - node.height / 2) }];
  }));
}
