import type { ReactNode, ThHTMLAttributes } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight } from 'lucide-react';

export function Table({ children, minWidth = 720 }: { children: ReactNode; minWidth?: number }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm" style={{ minWidth }}>
        {children}
      </table>
    </div>
  );
}

export function Th({ children, className = '', ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th scope="col" className={`table-head px-4 py-2.5 ${className}`} {...props}>
      {children}
    </th>
  );
}

export function SortableTh<T extends string>({ label, field, sort, direction, onSort, className = '' }: {
  label: string;
  field: T;
  sort: T;
  direction: 'asc' | 'desc';
  onSort: (field: T) => void;
  className?: string;
}) {
  const active = sort === field;
  const Icon = !active ? ArrowUpDown : direction === 'asc' ? ArrowUp : ArrowDown;
  return (
    <th scope="col" aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'} className={`table-head px-4 py-2.5 ${className}`}>
      <button type="button" onClick={() => onSort(field)} className="inline-flex items-center gap-1 uppercase hover:text-slate-900">
        {label}
        <Icon className={`h-3 w-3 ${active ? 'text-brand-700' : 'text-slate-400'}`} aria-hidden />
      </button>
    </th>
  );
}

export function Td({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <td className={`px-4 py-3 align-top text-slate-700 ${className}`}>{children}</td>;
}

export function Tr({ children, onClick, selected }: { children: ReactNode; onClick?: () => void; selected?: boolean }) {
  return (
    <tr
      onClick={onClick}
      className={`border-b border-slate-100 last:border-0 ${onClick ? 'cursor-pointer hover:bg-slate-50' : ''} ${selected ? 'bg-brand-50/60' : ''}`}
    >
      {children}
    </tr>
  );
}

export function Pagination({ page, totalPages, totalElements, size, onPage, onSize }: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPage: (page: number) => void;
  onSize?: (size: number) => void;
}) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-4 py-3 text-sm text-slate-600">
      <span>
        {from}–{to} of {totalElements.toLocaleString()}
      </span>
      <div className="flex items-center gap-3">
        {onSize && (
          <label className="flex items-center gap-2">
            <span className="text-xs">Rows</span>
            <select className="input h-8 w-20 py-0 text-xs" value={size} onChange={(event) => onSize(Number(event.target.value))}>
              {[10, 25, 50, 100].map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
        )}
        <div className="flex items-center gap-1">
          <button type="button" aria-label="Previous page" disabled={page <= 0} onClick={() => onPage(page - 1)}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-300 bg-white disabled:opacity-40">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-[84px] text-center text-xs">Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
          <button type="button" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-300 bg-white disabled:opacity-40">
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
