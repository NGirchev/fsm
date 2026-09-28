import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, type EdgeProps } from '@xyflow/react';
import { CircleDot, Zap } from 'lucide-react';
import { AUTO_COLOR, colorVariables } from './graphColors';

export function FlowEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  label,
  markerEnd,
  selected,
  data,
}: EdgeProps) {
  const [path, labelX, labelY] = getSmoothStepPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
  const onSelect = data?.onSelect as (() => void) | undefined;
  const color = (data?.color as { ink: string; fill: string } | undefined) ?? AUTO_COLOR;
  const automatic = data?.automatic === true;
  return <>
    <BaseEdge id={id} path={path} markerEnd={markerEnd}
      style={{ stroke: color.ink, strokeWidth: selected ? 3 : 2, strokeDasharray: automatic ? '7 5' : undefined }} />
    {label ? <EdgeLabelRenderer>
      <div
        data-id={id}
        className={`flow-edge-label nodrag nopan${selected ? ' selected' : ''}${automatic ? ' automatic' : ''}`}
        onClick={(event) => { event.stopPropagation(); onSelect?.(); }}
        style={{ ...colorVariables(color), transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
        title={typeof label === 'string' ? label : undefined}
      >
        {automatic ? <Zap size={12} aria-hidden /> : <CircleDot size={12} aria-hidden />}
        <span>{label}</span>
      </div>
    </EdgeLabelRenderer> : null}
  </>;
}
