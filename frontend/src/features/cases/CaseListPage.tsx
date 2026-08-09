import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Plus, Search, Trash2 } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { deleteCase, getCases } from '../../services/caseService';
import { friendlyError } from '../../services/errors';
import { useAuth } from '../../contexts/AuthContext';
import type { CasePriority, CaseStatus } from '../../types/cases';

const statuses: Array<'ALL' | CaseStatus> = ['ALL', 'OPEN', 'ACTIVE', 'ON_HOLD', 'CLOSED', 'ARCHIVED'];
const priorities: Array<'ALL' | CasePriority> = ['ALL', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

function label(value: string) {
  return value === 'ALL' ? value : value.replace('_', ' ');
}

export function CaseListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, hasRole } = useAuth();
  const canMutate = hasRole(['ADMIN', 'INVESTIGATOR']);
  const canDelete = hasRole(['ADMIN']);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<(typeof statuses)[number]>('ALL');
  const [priority, setPriority] = useState<(typeof priorities)[number]>('ALL');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [sortBy, setSortBy] = useState('updatedAt');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const [message, setMessage] = useState('');

  const queryKey = ['cases', { page, size, sortBy, sortDir, search, status, priority }];
  const { data, isLoading, error, refetch } = useQuery({
    queryKey,
    queryFn: () => getCases({
      page,
      size,
      sortBy,
      sortDir,
      search: search.trim() || undefined,
      status: status === 'ALL' ? undefined : status,
      priority: priority === 'ALL' ? undefined : priority
    })
  });

  const deleteMutation = useMutation({
    mutationFn: deleteCase,
    onSuccess: async () => {
      setMessage('Case deleted successfully.');
      await queryClient.invalidateQueries({ queryKey: ['cases'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    }
  });

  const rows = data?.items ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);

  function sort(column: string) {
    setPage(0);
    if (column === sortBy) {
      setSortDir((value) => (value === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSortBy(column);
    setSortDir('asc');
  }

  async function confirmDelete(id: number, caseNumber: string) {
    setMessage('');
    if (confirm(`Delete ${caseNumber}?`)) {
      try {
        await deleteMutation.mutateAsync(id);
      } catch {
        // Mutation error is rendered below.
      }
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
        <div>
          <h1 className="text-2xl font-semibold text-white">Case Management</h1>
          <p className="mt-1 text-sm text-slate-400">Server-side pagination, sorting, and forensic case filtering.</p>
        </div>
        {canMutate && (
          <Button onClick={() => navigate('/cases/new')}>
            <Plus className="h-4 w-4" /> Create Case
          </Button>
        )}
      </div>

      {(message || deleteMutation.error) && (
        <div className={`rounded-md border px-4 py-3 text-sm ${deleteMutation.error ? 'border-rose-400/30 bg-rose-400/10 text-rose-200' : 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200'}`}>
          {deleteMutation.error ? friendlyError(deleteMutation.error, 'Unable to delete case.') : message}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card><p className="text-sm text-slate-400">Total Matching Cases</p><p className="mt-2 text-2xl font-semibold text-white">{data?.totalElements ?? 0}</p></Card>
        <Card><p className="text-sm text-slate-400">Current Page</p><p className="mt-2 text-2xl font-semibold text-white">{data ? data.page + 1 : 1}</p></Card>
        <Card><p className="text-sm text-slate-400">Rows Loaded</p><p className="mt-2 text-2xl font-semibold text-white">{rows.length}</p></Card>
        <Card><p className="text-sm text-slate-400">Page Size</p><p className="mt-2 text-2xl font-semibold text-white">{data?.size ?? size}</p></Card>
      </div>

      <Card>
        <div className="grid gap-3 md:grid-cols-4">
          <div className="relative md:col-span-2">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
            <input
              className="w-full rounded-md border border-slate-700 bg-[#0d151c] py-2 pl-9 pr-3 text-sm text-slate-100"
              placeholder="Search by case number, title, investigator, category..."
              value={search}
              onChange={(event) => {
                setPage(0);
                setSearch(event.target.value);
              }}
            />
          </div>
          <select className="rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100" value={status} onChange={(event) => { setPage(0); setStatus(event.target.value as typeof status); }}>
            {statuses.map((item) => <option key={item} value={item}>{item === 'ALL' ? 'All Statuses' : label(item)}</option>)}
          </select>
          <select className="rounded-md border border-slate-700 bg-[#0d151c] px-3 py-2 text-sm text-slate-100" value={priority} onChange={(event) => { setPage(0); setPriority(event.target.value as typeof priority); }}>
            {priorities.map((item) => <option key={item} value={item}>{item === 'ALL' ? 'All Priorities' : label(item)}</option>)}
          </select>
        </div>
      </Card>

      <Card>
        {isLoading ? (
          <div className="py-10 text-center text-sm text-slate-400">Loading cases from backend...</div>
        ) : error ? (
          <div className="py-10 text-center">
            <p className="text-sm text-rose-300">{friendlyError(error, 'Unable to load cases.')}</p>
            <button className="mt-3 rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-white/5" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[980px] text-left text-sm">
                <thead className="text-xs uppercase text-slate-500">
                  <tr>
                    <th className="cursor-pointer pb-3" onClick={() => sort('caseNumber')}>Case #</th>
                    <th className="cursor-pointer pb-3" onClick={() => sort('title')}>Title</th>
                    <th className="cursor-pointer pb-3" onClick={() => sort('leadInvestigator')}>Investigator</th>
                    <th className="cursor-pointer pb-3" onClick={() => sort('status')}>Status</th>
                    <th className="cursor-pointer pb-3" onClick={() => sort('priority')}>Priority</th>
                    <th className="pb-3">Category</th>
                    <th className="cursor-pointer pb-3" onClick={() => sort('createdAt')}>Created</th>
                    <th className="pb-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {rows.map((item) => (
                    <tr key={item.id} className="text-slate-300">
                      <td className="py-3 font-semibold text-cyanline">{item.caseNumber}</td>
                      <td className="py-3">{item.title}</td>
                      <td className="py-3">{item.leadInvestigator}</td>
                      <td className="py-3">
                        <span className="rounded-md bg-blue-400/10 px-2 py-1 text-xs text-blue-300">{label(item.status)}</span>
                      </td>
                      <td className="py-3">
                        <span className={`rounded-md px-2 py-1 text-xs ${
                          item.priority === 'CRITICAL' ? 'bg-rose-400/10 text-rose-300' :
                          item.priority === 'HIGH' ? 'bg-amber-400/10 text-amber-300' :
                          item.priority === 'MEDIUM' ? 'bg-cyanline/10 text-cyanline' :
                          'bg-slate-500/10 text-slate-300'
                        }`}>{label(item.priority)}</span>
                      </td>
                      <td className="py-3">{item.category}</td>
                      <td className="py-3 text-slate-500">{new Date(item.createdAt).toLocaleDateString()}</td>
                      <td className="py-3">
                        <div className="flex items-center gap-2">
                          <Button className="bg-transparent px-2 py-1 text-cyanline hover:bg-cyanline/10" onClick={() => navigate(`/cases/${item.id}`)}>View</Button>
                          {canMutate && <Button className="bg-transparent px-2 py-1 text-slate-200 hover:bg-white/10" onClick={() => navigate(`/cases/${item.id}/edit`)}>Edit</Button>}
                          {canDelete && (
                            <button
                              className="rounded-md border border-rose-400/30 p-1.5 text-rose-300 hover:bg-rose-400/10 disabled:opacity-50"
                              disabled={deleteMutation.isPending}
                              onClick={() => confirmDelete(item.id, item.caseNumber)}
                              aria-label={`Delete ${item.caseNumber}`}
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {rows.length === 0 && (
              <div className="py-10 text-center text-sm text-slate-500">No cases match your current filters.</div>
            )}
          </>
        )}

        <div className="mt-4 flex flex-col gap-3 border-t border-slate-800 pt-4 md:flex-row md:items-center md:justify-between">
          <p className="text-sm text-slate-400">
            Showing page {Math.min(page + 1, totalPages)} of {totalPages} ({data?.totalElements ?? 0} total cases)
          </p>
          <div className="flex items-center gap-2">
            <select className="rounded-md border border-slate-700 bg-[#0d151c] px-2 py-1 text-sm text-slate-200" value={size} onChange={(event) => { setPage(0); setSize(Number(event.target.value)); }}>
              {[10, 20, 50].map((option) => <option key={option} value={option}>{option}/page</option>)}
            </select>
            <button className="rounded-md border border-slate-700 p-2 text-slate-300 disabled:opacity-40" disabled={page === 0 || isLoading} onClick={() => setPage((value) => Math.max(0, value - 1))}>
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button className="rounded-md border border-slate-700 p-2 text-slate-300 disabled:opacity-40" disabled={page >= totalPages - 1 || isLoading} onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))}>
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </Card>

      {!hasRole(['ADMIN']) && (
        <p className="text-xs text-slate-500">
          Signed in as {user?.username}. Delete is restricted to ADMIN. Investigators can update only assigned cases.
        </p>
      )}

      <div className="text-xs text-slate-500">
        Quick links: <Link className="text-cyanline" to="/dashboard">Dashboard</Link>
      </div>
    </div>
  );
}
