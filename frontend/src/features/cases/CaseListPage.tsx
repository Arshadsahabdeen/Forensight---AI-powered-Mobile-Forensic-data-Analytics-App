import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList, FolderPlus, Pencil, Trash2 } from 'lucide-react';
import { deleteCase, getCases } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { usePermissions } from '../../contexts/AuthContext';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { IconButton, LinkButton } from '../../components/ui/Button';
import { SearchInput, Select } from '../../components/ui/Input';
import { StatusBadge } from '../../components/ui/Badge';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Pagination, SortableTh, Table, Td, Th, Tr } from '../../components/ui/Table';
import { ConfirmDialog } from '../../components/ui/Dialog';
import { formatRelative } from '../../lib/format';
import type { CaseListItem } from '../../types/api';

type SortField = 'caseNumber' | 'title' | 'status' | 'priority' | 'leadInvestigator' | 'updatedAt';

export default function CaseListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { canWrite, isAdmin } = usePermissions();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sort, setSort] = useState<SortField>('updatedAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');
  const [pendingDelete, setPendingDelete] = useState<CaseListItem | null>(null);
  const debouncedSearch = useDebounce(search);

  const params = { page, size, sortBy: sort, sortDir: direction, search: debouncedSearch, status, priority };
  const cases = useQuery({ queryKey: ['cases', params], queryFn: () => getCases(params), placeholderData: keepPreviousData });

  const remove = useMutation({
    mutationFn: (id: number) => deleteCase(id),
    onSuccess: () => {
      toast.success('Case deleted.');
      setPendingDelete(null);
      void queryClient.invalidateQueries({ queryKey: ['cases'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (error) => {
      toast.error(friendlyError(error));
      setPendingDelete(null);
    }
  });

  function toggleSort(field: SortField) {
    if (field === sort) setDirection((value) => (value === 'asc' ? 'desc' : 'asc'));
    else {
      setSort(field);
      setDirection('asc');
    }
    setPage(0);
  }

  return (
    <>
      <PageHeader title="Cases" description="Investigations you can access."
        actions={canWrite && <LinkButton to="/cases/new" icon={<FolderPlus className="h-4 w-4" />}>New case</LinkButton>} />
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-[1fr_180px_180px]">
          <SearchInput aria-label="Search cases" placeholder="Search number, title, investigator, location…" value={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }} />
          <Select aria-label="Status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {['OPEN', 'ACTIVE', 'ON_HOLD', 'CLOSED', 'ARCHIVED'].map((value) => <option key={value} value={value}>{value.replace('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</option>)}
          </Select>
          <Select aria-label="Priority" value={priority} onChange={(event) => { setPriority(event.target.value); setPage(0); }}>
            <option value="">All priorities</option>
            {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((value) => <option key={value} value={value}>{value.charAt(0) + value.slice(1).toLowerCase()}</option>)}
          </Select>
        </div>
        {cases.isLoading ? (
          <SkeletonRows rows={8} columns={5} />
        ) : cases.isError ? (
          <ErrorState message={friendlyError(cases.error)} onRetry={() => void cases.refetch()} />
        ) : cases.data && cases.data.items.length === 0 ? (
          <EmptyState icon={<ClipboardList className="h-6 w-6" />} title={search || status || priority ? 'No cases match these filters' : 'No cases yet'}
            description={canWrite && !search ? 'Create a case to start an investigation.' : undefined}
            action={canWrite && !search && !status && !priority ? <LinkButton to="/cases/new">New case</LinkButton> : undefined} />
        ) : cases.data ? (
          <>
            <Table minWidth={860}>
              <thead>
                <tr>
                  <SortableTh label="Case" field="caseNumber" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Title" field="title" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Status" field="status" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Priority" field="priority" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Lead" field="leadInvestigator" sort={sort} direction={direction} onSort={toggleSort} />
                  <SortableTh label="Updated" field="updatedAt" sort={sort} direction={direction} onSort={toggleSort} />
                  <Th className="text-right"><span className="sr-only">Actions</span></Th>
                </tr>
              </thead>
              <tbody className={cases.isFetching ? 'opacity-60' : ''}>
                {cases.data.items.map((item) => (
                  <Tr key={item.id} onClick={() => navigate(`/cases/${item.id}`)}>
                    <Td><Link to={`/cases/${item.id}`} onClick={(event) => event.stopPropagation()} className="link">{item.caseNumber}</Link></Td>
                    <Td className="max-w-xs"><p className="truncate text-slate-900">{item.title}</p><p className="text-xs text-slate-500">{item.category}</p></Td>
                    <Td><StatusBadge value={item.status} /></Td>
                    <Td><StatusBadge value={item.priority} /></Td>
                    <Td>{item.leadInvestigator}</Td>
                    <Td className="whitespace-nowrap text-xs text-slate-500">{formatRelative(item.updatedAt)}</Td>
                    <Td className="text-right">
                      <div className="flex justify-end gap-1" onClick={(event) => event.stopPropagation()}>
                        {canWrite && (
                          <IconButton label={`Edit ${item.caseNumber}`} onClick={() => navigate(`/cases/${item.id}/edit`)}>
                            <Pencil className="h-4 w-4" />
                          </IconButton>
                        )}
                        {isAdmin && (
                          <IconButton label={`Delete ${item.caseNumber}`} onClick={() => setPendingDelete(item)} className="hover:text-red-600">
                            <Trash2 className="h-4 w-4" />
                          </IconButton>
                        )}
                      </div>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={cases.data.page} totalPages={cases.data.totalPages} totalElements={cases.data.totalElements} size={size}
              onPage={setPage} onSize={(value) => { setSize(value); setPage(0); }} />
          </>
        ) : null}
      </Card>
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete case?"
        confirmLabel="Delete case"
        loading={remove.isPending}
        message={<>Delete <strong>{pendingDelete?.caseNumber}</strong>? Only cases without evidence can be deleted; cases holding evidence must be archived instead. This cannot be undone.</>}
        onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
