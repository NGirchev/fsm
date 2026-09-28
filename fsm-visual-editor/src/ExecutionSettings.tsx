import type { Dispatch, SetStateAction } from 'react';
import { Play } from 'lucide-react';
import type { CatalogBehavior, FlowExecution, FsmEditorDocument } from './domain';
import { CatalogPicker } from './CatalogPicker';
import { Panel } from './Panel';

export function ExecutionSettings({ catalog, document, setDocument }: {
  catalog: CatalogBehavior[];
  document: FsmEditorDocument;
  setDocument: Dispatch<SetStateAction<FsmEditorDocument>>;
}) {
  const execution = document.execution ?? { stateListeners: [], completionListeners: [] };
  const update = (patch: Partial<FlowExecution>) => setDocument((current) => ({
    ...current, execution: { ...execution, ...patch },
  }));
  const count = execution.stateListeners.length + execution.completionListeners.length;
  return <Panel title="Execution settings" icon={<Play size={15} aria-hidden />} collapsible count={count}>
    {(['stateListeners', 'completionListeners'] as const).map((kind) => <CatalogPicker key={kind}
      title={kind === 'stateListeners' ? 'State listeners' : 'Completion listeners'}
      options={catalog.filter((item) => item.kind === (kind === 'stateListeners' ? 'stateListener' : 'completionListener'))}
      selected={execution[kind]} onChange={(next) => update({ [kind]: next })} />)}
  </Panel>;
}
