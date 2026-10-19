import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { Download, ShieldCheck } from 'lucide-react';
import { downloadFile } from '../../services/api';
import { compact, getAuditActions, getAuditLogs, verifyAuditChain } from '../../services/forensics';
import { friendlyError } from '../../services/errors';
import { useDebounce } from '../../hooks/useDebounce';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input, SearchInput, Select } from '../../components/ui/Input';
import { StatusBadge } from '../../components/ui/Badge';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Pagination, Table, Td, Th, Tr } from '../../components/ui/Table';
import { formatUtc, utcInputToIso } from '../../lib/format';
import type { ChainVerification } from '../../types/api';

export default function AuditLogsPage() {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [action, setAction] = useState('');
  const [username, setUsername] = useState('');
  const [result, setResult] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [chain, setChain] = useState<ChainVerification | null>(null);
  const debouncedSearch = useDebounce(search);
  const debouncedUser = useDebounce(username);

  const filters = { action, username: debouncedUser, success: result === '' ? undefined : result === 'success', search: debouncedSearch, from: utcInputToIso(from), to: utcInputToIso(to) };
  const logs = useQuery({ queryKey: ['audit', filters, page, size], queryFn: () => getAuditLogs({ ...filters, page, size }), placeholderData: keepPreviousData });
  const actions = useQuery({ queryKey: ['audit-actions'], queryFn: getAuditActions, staleTime: 60_000 });
  const verify = useMutation({ mutationFn: verifyAuditChain, onSuccess: setChain, onError: (error) => toast.error(friendlyError(error)) });

  const reset = () => setPage(0);

  return (
    <>
      <PageHeader title="Audit log" description="Append-only, hash-chained record of security and investigation activity. Records cannot be edited or deleted through the platform."
        actions={<>
          <Button variant="secondary" icon={<ShieldCheck className="h-4 w-4" />} loading={verify.isPending} onClick={() => verify.mutate()}>Verify hash chain</Button>
          <Button variant="secondary" icon={<Download className="h-4 w-4" />}
            onClick={() => downloadFile('/audit/export', 'audit-log.csv', compact(filters)).catch((error) => toast.error(friendlyError(error)))}>Export CSV</Button>
        </>} />
      {chain && (
        <div className="mb-4">
          <Alert tone={chain.status === 'INTACT' ? 'success' : 'danger'} title={chain.status === 'INTACT' ? 'Hash chain intact' : `Hash chain broken at record #${chain.firstBrokenRecordId}`}>
            {chain.detail} {chain.legacyUnchainedRecords > 0 && `${chain.legacyUnchainedRecords} older record(s) predate the chain and cannot be verified.`} Checked {formatUtc(chain.verifiedAt)}.
          </Alert>
        </div>
      )}
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 md:grid-cols-3 xl:grid-cols-6">
          <SearchInput className="xl:col-span-2" aria-label="Search" placeholder="Search description, actor, action, IP…" value={search} onChange={(event) => { setSearch(event.target.value); reset(); }} />
          <Select aria-label="Action" value={action} onChange={(event) => { setAction(event.target.value); reset(); }}>
            <option value="">All actions</option>
            {actions.data?.map((value) => <option key={value} value={value}>{value}</option>)}
          </Select>
          <Input aria-label="Username" placeholder="Username" value={username} onChange={(event) => { setUsername(event.target.value); reset(); }} />
          <Select aria-label="Result" value={result} onChange={(event) => { setResult(event.target.value); reset(); }}>
            <option value="">All results</option>
            <option value="success">Successful</option>
            <option value="failed">Failed / denied</option>
          </Select>
          <div className="flex gap-2 xl:col-span-6">
            <Input aria-label="From (UTC)" type="datetime-local" value={from} onChange={(event) => { setFrom(event.target.value); reset(); }} className="max-w-[220px]" />
            <Input aria-label="To (UTC)" type="datetime-local" value={to} onChange={(event) => { setTo(event.target.value); reset(); }} className="max-w-[220px]" />
          </div>
        </div>
        {logs.isLoading ? <SkeletonRows rows={10} columns={5} /> : logs.isError ? (
          <ErrorState message={friendlyError(logs.error)} onRetry={() => void logs.refetch()} />
        ) : logs.data && logs.data.items.length === 0 ? (
          <EmptyState title="No audit records match" />
        ) : logs.data ? (
          <>
            <Table minWidth={960}>
              <thead><tr><Th>#</Th><Th>Time (UTC)</Th><Th>Actor</Th><Th>Action</Th><Th>Description</Th><Th>Context</Th><Th>Result</Th></tr></thead>
              <tbody className={logs.isFetching ? 'opacity-60' : ''}>
                {logs.data.items.map((log) => (
                  <Tr key={log.id}>
                    <Td className="mono">{log.id}{!log.chained && <p className="text-slate-400" title="Recorded before the hash chain existed">legacy</p>}</Td>
                    <Td className="whitespace-nowrap text-xs">{formatUtc(log.timestamp)}</Td>
                    <Td>{log.username ?? 'system'}</Td>
                    <Td className="mono">{log.action}</Td>
                    <Td className="max-w-md text-xs">{log.description}</Td>
                    <Td className="text-xs text-slate-500">
                      {log.caseId && <p>case #{log.caseId}</p>}
                      {log.entityType && <p>{log.entityType.toLowerCase()} #{log.entityId}</p>}
                      {log.ipAddress && <p>{log.ipAddress}</p>}
                    </Td>
                    <Td><StatusBadge value={log.success ? 'SUCCEEDED' : 'FAILED'} label={log.success ? 'Success' : 'Failed'} /></Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={logs.data.page} totalPages={logs.data.totalPages} totalElements={logs.data.totalElements} size={size}
              onPage={setPage} onSize={(value) => { setSize(value); setPage(0); }} />
          </>
        ) : null}
      </Card>
    </>
  );
}
