import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute, RoleProtectedRoute } from './routes/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './features/authentication/LoginPage';
import { NotFoundPage, UnauthorizedPage } from './features/authentication/UnauthorizedPage';
import { Spinner } from './components/ui/Feedback';

const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage'));
const CaseListPage = lazy(() => import('./features/cases/CaseListPage'));
const CaseFormPage = lazy(() => import('./features/cases/CaseFormPage'));
const CaseDetailPage = lazy(() => import('./features/cases/CaseDetailPage'));
const DeviceListPage = lazy(() => import('./features/devices/DeviceListPage'));
const DeviceFormPage = lazy(() => import('./features/devices/DeviceFormPage'));
const DeviceDetailPage = lazy(() => import('./features/devices/DeviceDetailPage'));
const EvidenceSearchPage = lazy(() => import('./features/evidence/EvidenceSearchPage'));
const TimelinePage = lazy(() => import('./features/timeline/TimelinePage'));
const RelationshipsPage = lazy(() => import('./features/relationships/RelationshipsPage'));
const AssistantPage = lazy(() => import('./features/assistant/AssistantPage'));
const ReportsPage = lazy(() => import('./features/reports/ReportsPage'));
const ReportViewPage = lazy(() => import('./features/reports/ReportViewPage'));
const ProcessingPage = lazy(() => import('./features/processing/ProcessingPage'));
const AuditLogsPage = lazy(() => import('./features/audit/AuditLogsPage'));
const UserAdminPage = lazy(() => import('./features/users/UserAdminPage'));
const SettingsPage = lazy(() => import('./features/settings/SettingsPage'));

export default function App() {
  return (
    <Suspense fallback={<div className="p-8"><Spinner /></div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/unauthorized" element={<UnauthorizedPage />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />

            <Route path="/cases" element={<CaseListPage />} />
            <Route path="/devices" element={<DeviceListPage />} />
            <Route path="/evidence" element={<EvidenceSearchPage />} />
            <Route path="/timeline" element={<TimelinePage />} />
            <Route path="/relationships" element={<RelationshipsPage />} />
            <Route path="/assistant" element={<AssistantPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/reports/:id" element={<ReportViewPage />} />
            <Route path="/processing" element={<ProcessingPage />} />

            <Route element={<RoleProtectedRoute roles={['ADMIN', 'INVESTIGATOR']} />}>
              <Route path="/cases/new" element={<CaseFormPage />} />
              <Route path="/cases/:id/edit" element={<CaseFormPage />} />
              <Route path="/devices/new" element={<DeviceFormPage />} />
              <Route path="/devices/:id/edit" element={<DeviceFormPage />} />
            </Route>
            <Route path="/cases/:id" element={<CaseDetailPage />} />
            <Route path="/devices/:id" element={<DeviceDetailPage />} />

            <Route element={<RoleProtectedRoute roles={['ADMIN']} />}>
              <Route path="/audit" element={<AuditLogsPage />} />
              <Route path="/users" element={<UserAdminPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
