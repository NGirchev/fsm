import { useState, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';

export function Panel({
  title,
  icon,
  count,
  actions,
  collapsible = false,
  defaultOpen = false,
  className = '',
  children,
}: {
  title: string;
  icon?: ReactNode;
  count?: number;
  actions?: ReactNode;
  collapsible?: boolean;
  defaultOpen?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const head = (
    <h2>
      {icon && <span className="panel-icon">{icon}</span>}
      {title}
      {count !== undefined && <span className="count">{count}</span>}
    </h2>
  );
  const actionsSlot = actions
    ? <span className="head-actions" onClick={(event) => { event.preventDefault(); event.stopPropagation(); }}>{actions}</span>
    : null;

  if (!collapsible) {
    return (
      <section className={`panel ${className}`.trim()}>
        <div className="panel-head static">
          {head}
          {actionsSlot}
        </div>
        <div className="panel-body">{children}</div>
      </section>
    );
  }

  return (
    <details
      className={`panel ${className}`.trim()}
      open={open}
      onToggle={(event) => setOpen(event.currentTarget.open)}
    >
      <summary className="panel-head">
        {head}
        {actionsSlot}
        <ChevronDown className="chevron" size={16} aria-hidden />
      </summary>
      <div className="panel-body">{children}</div>
    </details>
  );
}
