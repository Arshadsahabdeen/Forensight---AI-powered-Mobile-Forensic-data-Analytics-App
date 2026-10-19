import type { ReactNode } from 'react';

export interface TabItem {
  id: string;
  label: string;
  icon?: ReactNode;
  count?: number;
}

export function Tabs({ tabs, active, onChange, label }: { tabs: TabItem[]; active: string; onChange: (id: string) => void; label: string }) {
  return (
    <div className="-mb-px overflow-x-auto border-b border-slate-200">
      <div role="tablist" aria-label={label} className="flex min-w-max gap-1">
        {tabs.map((tab) => {
          const selected = tab.id === active;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`panel-${tab.id}`}
              onClick={() => onChange(tab.id)}
              className={`inline-flex items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors ${
                selected ? 'border-brand-700 text-brand-800' : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800'
              }`}
            >
              {tab.icon}
              {tab.label}
              {tab.count !== undefined && (
                <span className={`rounded-full px-1.5 text-xs ${selected ? 'bg-brand-100 text-brand-800' : 'bg-slate-100 text-slate-600'}`}>{tab.count}</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
