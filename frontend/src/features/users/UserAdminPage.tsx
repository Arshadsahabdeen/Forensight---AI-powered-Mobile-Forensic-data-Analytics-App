import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRound, Lock, Unlock, UserPlus } from 'lucide-react';
import { createUser, getUsers, resetPassword, updateUserRole, updateUserStatus } from '../../services/forensics';
import { fieldErrors, friendlyError } from '../../services/errors';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Field, Input, SearchInput, Select } from '../../components/ui/Input';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Alert, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { ConfirmDialog, Dialog } from '../../components/ui/Dialog';
import { formatRelative } from '../../lib/format';
import type { AdminUser } from '../../types/api';

export const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{10,128}$/;
const PASSWORD_HINT = 'At least 10 characters with upper-case, lower-case and a digit.';

export default function UserAdminPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { user: me } = useAuth();
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [resetFor, setResetFor] = useState<AdminUser | null>(null);
  const [statusChange, setStatusChange] = useState<{ user: AdminUser; enabled: boolean } | null>(null);
  const users = useQuery({ queryKey: ['users'], queryFn: getUsers });
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['users'] });

  const role = useMutation({
    mutationFn: ({ id, value }: { id: number; value: string }) => updateUserRole(id, value),
    onSuccess: () => { toast.success('Role updated.'); invalidate(); },
    onError: (error) => { toast.error(friendlyError(error)); invalidate(); }
  });
  const status = useMutation({
    mutationFn: ({ id, enabled, locked }: { id: number; enabled: boolean; locked: boolean }) => updateUserStatus(id, enabled, locked),
    onSuccess: () => { toast.success('Account updated.'); setStatusChange(null); invalidate(); },
    onError: (error) => { toast.error(friendlyError(error)); setStatusChange(null); }
  });

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (users.data ?? []).filter((u) => !term || `${u.username} ${u.fullName} ${u.email} ${u.roles.join(' ')}`.toLowerCase().includes(term));
  }, [users.data, search]);

  return (
    <>
      <PageHeader title="Users" description="Accounts and roles. Accounts are disabled rather than deleted so audit records stay attributable."
        actions={<Button icon={<UserPlus className="h-4 w-4" />} onClick={() => setCreateOpen(true)}>New user</Button>} />
      <Card>
        <div className="border-b border-slate-200 p-4">
          <SearchInput aria-label="Search users" placeholder="Search name, username, email or role" value={search} onChange={(event) => setSearch(event.target.value)} className="max-w-md" />
        </div>
        {users.isLoading ? <SkeletonRows rows={6} columns={4} /> : users.isError ? (
          <ErrorState message={friendlyError(users.error)} onRetry={() => void users.refetch()} />
        ) : filtered.length === 0 ? <EmptyState title="No users match" /> : (
          <Table minWidth={880}>
            <thead><tr><Th>User</Th><Th>Role</Th><Th>Status</Th><Th>Last sign-in</Th><Th className="text-right">Actions</Th></tr></thead>
            <tbody>
              {filtered.map((u) => {
                const self = u.username === me?.username;
                return (
                  <Tr key={u.id}>
                    <Td><p className="font-medium text-slate-900">{u.fullName} {self && <Badge tone="brand">You</Badge>}</p><p className="text-xs text-slate-500">@{u.username} · {u.email}</p></Td>
                    <Td>
                      <Select aria-label={`Role for ${u.username}`} className="h-8 w-40 py-0 text-xs" value={u.roles[0]} disabled={role.isPending || self}
                        onChange={(event) => role.mutate({ id: u.id, value: event.target.value })}>
                        <option value="ADMIN">Administrator</option>
                        <option value="INVESTIGATOR">Investigator</option>
                        <option value="REVIEWER">Reviewer</option>
                      </Select>
                    </Td>
                    <Td>
                      <div className="flex flex-wrap gap-1">
                        <StatusBadge value={u.enabled ? 'SUCCEEDED' : 'FAILED'} label={u.enabled ? 'Enabled' : 'Disabled'} />
                        {u.accountLocked && <Badge tone="warning">Locked</Badge>}
                      </div>
                    </Td>
                    <Td className="text-xs text-slate-500">{u.lastLoginAt ? formatRelative(u.lastLoginAt) : 'Never'}</Td>
                    <Td className="text-right">
                      <div className="flex flex-wrap justify-end gap-1">
                        {u.accountLocked && (
                          <Button size="sm" variant="secondary" icon={<Unlock className="h-3.5 w-3.5" />} onClick={() => status.mutate({ id: u.id, enabled: u.enabled, locked: false })}>Unlock</Button>
                        )}
                        <Button size="sm" variant="secondary" icon={<KeyRound className="h-3.5 w-3.5" />} onClick={() => setResetFor(u)}>Reset password</Button>
                        {!self && (
                          <Button size="sm" variant={u.enabled ? 'ghost' : 'secondary'} icon={u.enabled ? <Lock className="h-3.5 w-3.5" /> : <Unlock className="h-3.5 w-3.5" />}
                            onClick={() => setStatusChange({ user: u, enabled: !u.enabled })}>{u.enabled ? 'Disable' : 'Enable'}</Button>
                        )}
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>
      <CreateUserDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={invalidate} />
      <ResetPasswordDialog user={resetFor} onClose={() => setResetFor(null)} onDone={invalidate} />
      <ConfirmDialog open={statusChange !== null} title={statusChange?.enabled ? 'Enable account?' : 'Disable account?'}
        confirmLabel={statusChange?.enabled ? 'Enable' : 'Disable'} tone={statusChange?.enabled ? 'primary' : 'danger'} loading={status.isPending}
        message={statusChange?.enabled ? `${statusChange.user.fullName} will be able to sign in again.` : `${statusChange?.user.fullName} will be signed out and unable to sign in.`}
        onConfirm={() => statusChange && status.mutate({ id: statusChange.user.id, enabled: statusChange.enabled, locked: false })}
        onCancel={() => setStatusChange(null)} />
    </>
  );
}

function CreateUserDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const toast = useToast();
  const [values, setValues] = useState({ username: '', fullName: '', email: '', password: '', role: 'INVESTIGATOR' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const create = useMutation({
    mutationFn: () => createUser(values),
    onSuccess: () => {
      toast.success('User created.');
      setValues({ username: '', fullName: '', email: '', password: '', role: 'INVESTIGATOR' });
      onCreated();
      onClose();
    },
    onError: (error) => setErrors({ form: friendlyError(error), ...fieldErrors(error) })
  });
  function set(key: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: '' }));
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!/^[a-zA-Z0-9._-]{3,60}$/.test(values.username)) next.username = '3–60 letters, digits, ".", "_" or "-".';
    if (!values.fullName.trim()) next.fullName = 'Full name is required.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) next.email = 'Enter a valid email address.';
    if (!PASSWORD_RULE.test(values.password)) next.password = PASSWORD_HINT;
    setErrors(next);
    if (!Object.keys(next).length) create.mutate();
  }
  return (
    <Dialog open={open} onClose={onClose} title="New user"
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="create-user" loading={create.isPending}>Create user</Button></>}>
      <form id="create-user" onSubmit={submit} noValidate className="space-y-4">
        {errors.form && <Alert tone="danger">{errors.form}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Username" required error={errors.username}>{(p) => <Input {...p} autoComplete="off" value={values.username} onChange={(e) => set('username', e.target.value)} />}</Field>
          <Field label="Role" required>
            {(p) => (
              <Select {...p} value={values.role} onChange={(e) => set('role', e.target.value)}>
                <option value="INVESTIGATOR">Investigator</option>
                <option value="REVIEWER">Reviewer</option>
                <option value="ADMIN">Administrator</option>
              </Select>
            )}
          </Field>
        </div>
        <Field label="Full name" required error={errors.fullName} hint="Used to match the lead investigator on cases.">{(p) => <Input {...p} value={values.fullName} onChange={(e) => set('fullName', e.target.value)} />}</Field>
        <Field label="Email" required error={errors.email}>{(p) => <Input {...p} type="email" value={values.email} onChange={(e) => set('email', e.target.value)} />}</Field>
        <Field label="Initial password" required error={errors.password} hint={PASSWORD_HINT}>{(p) => <Input {...p} type="password" autoComplete="new-password" value={values.password} onChange={(e) => set('password', e.target.value)} />}</Field>
      </form>
    </Dialog>
  );
}

function ResetPasswordDialog({ user, onClose, onDone }: { user: AdminUser | null; onClose: () => void; onDone: () => void }) {
  const toast = useToast();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const reset = useMutation({
    mutationFn: () => resetPassword(user!.id, password),
    onSuccess: () => { toast.success('Password reset. Existing sessions were signed out.'); setPassword(''); onDone(); onClose(); },
    onError: (err) => setError(friendlyError(err))
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    if (!PASSWORD_RULE.test(password)) { setError(PASSWORD_HINT); return; }
    reset.mutate();
  }
  return (
    <Dialog open={user !== null} onClose={onClose} title="Reset password" description={user ? `${user.fullName} (@${user.username})` : undefined}
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="reset-password" loading={reset.isPending}>Reset password</Button></>}>
      <form id="reset-password" onSubmit={submit} noValidate className="space-y-3">
        <Field label="New password" required error={error || undefined} hint={PASSWORD_HINT}>
          {(p) => <Input {...p} type="password" autoComplete="new-password" value={password} onChange={(e) => { setPassword(e.target.value); setError(''); }} />}
        </Field>
        <p className="text-xs text-slate-500">Share the new password through a secure channel. The user's existing sessions are revoked.</p>
      </form>
    </Dialog>
  );
}
