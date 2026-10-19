import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Role } from '../types/auth';

const authState = {
  user: null as null | { id: number; username: string; fullName: string; email: string; roles: Role[]; updatedAt: string },
  isLoading: false,
  isAuthenticated: false,
  login: vi.fn(),
  logout: vi.fn()
};

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({ ...authState, hasRole: (roles: string[]) => Boolean(authState.user?.roles.some((r) => roles.includes(r))) }),
  usePermissions: () => ({ isAdmin: authState.user?.roles.includes('ADMIN') ?? false, canWrite: true, canReview: false })
}));

import { ProtectedRoute, RoleProtectedRoute } from '../routes/ProtectedRoute';
import { Sidebar } from '../components/layout/Sidebar';
import { LoginPage } from '../features/authentication/LoginPage';

function signIn(role: Role) {
  authState.user = { id: 1, username: role.toLowerCase(), fullName: `${role} User`, email: 'x@y.z', roles: [role], updatedAt: '' };
  authState.isAuthenticated = true;
}

beforeEach(() => {
  authState.user = null;
  authState.isAuthenticated = false;
  authState.login.mockReset();
});

function renderRoutes(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<p>login page</p>} />
        <Route path="/unauthorized" element={<p>unauthorized page</p>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/cases" element={<p>cases page</p>} />
          <Route element={<RoleProtectedRoute roles={['ADMIN']} />}>
            <Route path="/audit" element={<p>audit page</p>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

describe('route guards', () => {
  it('sends anonymous users to the sign-in page', () => {
    renderRoutes('/cases');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('blocks non-administrators from admin pages', () => {
    signIn('REVIEWER');
    renderRoutes('/audit');
    expect(screen.getByText('unauthorized page')).toBeInTheDocument();
  });

  it('lets administrators through', () => {
    signIn('ADMIN');
    renderRoutes('/audit');
    expect(screen.getByText('audit page')).toBeInTheDocument();
  });
});

describe('navigation', () => {
  it('shows administration links only to administrators', () => {
    signIn('INVESTIGATOR');
    const { unmount } = render(<MemoryRouter><Sidebar open={false} onClose={() => undefined} /></MemoryRouter>);
    expect(screen.getAllByRole('link', { name: 'Cases' }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('link', { name: 'Audit log' })).not.toBeInTheDocument();
    unmount();

    signIn('ADMIN');
    render(<MemoryRouter><Sidebar open={false} onClose={() => undefined} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'Audit log' })).toHaveAttribute('href', '/audit');
  });
});

describe('sign-in form', () => {
  it('validates required fields before calling the API', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Enter your username or email.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(authState.login).not.toHaveBeenCalled();
  });

  it('submits trimmed credentials and shows server errors', async () => {
    const user = userEvent.setup();
    authState.login.mockRejectedValueOnce(new Error('Invalid username or password'));
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    await user.type(screen.getByLabelText('Username or email'), '  admin ');
    await user.type(screen.getByLabelText('Password'), 'secret');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(authState.login).toHaveBeenCalledWith('admin', 'secret');
    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument();
  });

  it('explains why a session ended', () => {
    window.sessionStorage.setItem('forensight_session_notice', 'You were signed out after 30 minutes of inactivity.');
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByText('You were signed out after 30 minutes of inactivity.')).toBeInTheDocument();
  });
});
