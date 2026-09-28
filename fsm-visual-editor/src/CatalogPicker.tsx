import { ChevronDown, ChevronUp, X } from 'lucide-react';
import type { CatalogBehavior } from './domain';
import { moveItem } from './domain/ordering';

export function CatalogPicker({ title, options, selected, onChange }: {
  title: string;
  options: CatalogBehavior[];
  selected: string[];
  onChange: (next: string[]) => void;
}) {
  const description = (id: string) => {
    const bean = options.find((item) => item.id === id);
    return bean ? bean.description : `Unavailable bean: ${id}`;
  };
  const choices = (current?: string) => options
    .filter((item) => item.id === current || !selected.includes(item.id))
    .map((item) => <option key={item.id} value={item.id} title={description(item.id)}>{item.id}</option>);
  return <fieldset className="check-group">
    <legend>{title}</legend>
    {selected.length > 0 && <ol className="picker-list" aria-label={`${title} execution order`}>
      {selected.map((id, index) => <li key={id} className="picker-row" title={description(id)}>
        <select aria-label={`${title} ${index + 1}`} title={description(id)} value={id}
          onChange={(event) => onChange(selected.map((value, position) => position === index ? event.target.value : value))}>
          {!options.some((item) => item.id === id) && <option value={id}>{id} (unavailable)</option>}
          {choices(id)}
        </select>
        <span className="picker-actions">
          {([-1, 1] as const).map((direction) => <button type="button" key={direction} className="small-button"
            aria-label={`Move ${title} ${id} ${direction === -1 ? 'up' : 'down'}`}
            disabled={index + direction < 0 || index + direction >= selected.length}
            onClick={() => onChange(moveItem(selected, index, direction))}>
            {direction === -1 ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>)}
          <button type="button" className="small-button danger" aria-label={`Remove ${title} ${id}`}
            onClick={() => onChange(selected.filter((value) => value !== id))}><X size={14} /></button>
        </span>
      </li>)}
    </ol>}
    <select aria-label={`Add ${title}`} value="" disabled={options.every((item) => selected.includes(item.id))}
      onChange={(event) => { if (event.target.value) onChange([...selected, event.target.value]); }}>
      <option value="">Choose a bean…</option>
      {choices()}
    </select>
  </fieldset>;
}
