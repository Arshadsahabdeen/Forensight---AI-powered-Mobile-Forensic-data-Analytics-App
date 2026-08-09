import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute, RoleProtectedRoute } from './routes/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';

import { LoginPage } from './features/authentication/LoginPage';
import { UnauthorizedPage } from './features/authentication/UnauthorizedPage';

import { DashboardPage } from './features/dashboard/DashboardPage';
import { ModulePage } from './features/dashboard/ModulePage';

import { CaseListPage } from './features/cases/CaseListPage';
import { CaseDetailPage } from './features/cases/CaseDetailPage';
import { CaseCreatePage } from './features/cases/CaseCreatePage';
import { CaseEditPage } from './features/cases/CaseEditPage';

import { DeviceListPage } from './features/devices/DeviceListPage';
import { DeviceCreatePage } from './features/devices/DeviceCreatePage';
import { DeviceEditPage } from './features/devices/DeviceEditPage';
import { DeviceDetailPage } from './features/devices/DeviceDetailPage';

import { EvidenceListPage } from './features/evidence/EvidenceListPage';
import { TimelinePage } from './features/timeline/TimelinePage';

import type { Role } from './types/auth';

const modules = [
  ['reports', 'Report Builder', ['ADMIN', 'INVESTIGATOR', 'REVIEWER']],
  ['audit', 'Audit Logs', ['ADMIN']],
  ['ai', 'AI Analysis Center', ['ADMIN', 'INVESTIGATOR']],
  ['users', 'User Administration', ['ADMIN']],
  ['settings', 'Platform Settings', ['ADMIN']]
] as const;

export default function App() {
  return (
    <Routes>

      {/* Public Routes */}

      <Route path="/login" element={<LoginPage />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />

      {/* Protected Routes */}

      <Route element={<ProtectedRoute />}>

        <Route element={<AppLayout />}>

          <Route path="/" element={<Navigate to="/dashboard" replace />} />

          <Route path="/dashboard" element={<DashboardPage />} />

          {/* Read Access */}

          <Route
            element={
              <RoleProtectedRoute
                roles={['ADMIN', 'INVESTIGATOR', 'REVIEWER']}
              />
            }
          >

            <Route path="/cases" element={<CaseListPage />} />

            <Route path="/cases/:id" element={<CaseDetailPage />} />

            <Route path="/devices" element={<DeviceListPage />} />

            <Route path="/devices/:id" element={<DeviceDetailPage />} />

            <Route path="/evidence" element={<EvidenceListPage />} />

            <Route path="/timeline" element={<TimelinePage />} />

          </Route>

          {/* Create / Update Access */}

          <Route
            element={
              <RoleProtectedRoute
                roles={['ADMIN', 'INVESTIGATOR']}
              />
            }
          >

            <Route path="/cases/new" element={<CaseCreatePage />} />

            <Route path="/cases/:id/edit" element={<CaseEditPage />} />

            <Route path="/devices/new" element={<DeviceCreatePage />} />

            <Route path="/devices/:id/edit" element={<DeviceEditPage />} />

          </Route>

          {/* Admin / Other Modules */}

          {modules.map(([path, title, roles]) => (

            <Route
              key={path}
              element={
                <RoleProtectedRoute
                  roles={[...roles] as Role[]}
                />
              }
            >

              <Route
                path={`/${path}`}
                element={<ModulePage title={title} />}
              />

            </Route>

          ))}

        </Route>

      </Route>

    </Routes>
  );
}