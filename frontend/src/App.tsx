import { Navigate, Route, Routes, useParams } from "react-router-dom";

import {
  ProtectedRoute,
  RoleProtectedRoute,
} from "./routes/ProtectedRoute";

import { AppLayout } from "./components/layout/AppLayout";

// Authentication
import { LoginPage } from "./features/authentication/LoginPage";
import { UnauthorizedPage } from "./features/authentication/UnauthorizedPage";

// Dashboard
import { DashboardPage } from "./features/dashboard/DashboardPage";

// Cases
import { CaseListPage } from "./features/cases/CaseListPage";
import { CaseDetailPage } from "./features/cases/CaseDetailPage";
import { CaseCreatePage } from "./features/cases/CaseCreatePage";
import { CaseEditPage } from "./features/cases/CaseEditPage";

// Devices
import { DeviceListPage } from "./features/devices/DeviceListPage";
import { DeviceCreatePage } from "./features/devices/DeviceCreatePage";
import { DeviceEditPage } from "./features/devices/DeviceEditPage";
import { DeviceDetailPage } from "./features/devices/DeviceDetailPage";

// Evidence
import { EvidenceListPage } from "./features/evidence/EvidenceListPage";

// Timeline
import { TimelinePage } from "./features/timeline/TimelinePage";

// Reports
import { ReportPage } from "./features/reports/ReportPage";
import { ReportsHubPage } from "./features/reports/ReportsHubPage";
import { AIAnalysisPage } from "./features/ai/AIAnalysisPage";
import { UserAdminPage } from "./features/users/UserAdminPage";
import { AuditLogsPage } from "./features/audit/AuditLogsPage";
import { SettingsPage } from "./features/settings/SettingsPage";

import type { Role } from "./types/auth";

export default function App() {
  return (
    <Routes>
      {/* =====================================================
          PUBLIC ROUTES
      ===================================================== */}

      <Route
        path="/login"
        element={<LoginPage />}
      />

      <Route
        path="/unauthorized"
        element={<UnauthorizedPage />}
      />

      {/* =====================================================
          PROTECTED ROUTES
      ===================================================== */}

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>

          {/* =================================================
              DASHBOARD
          ================================================= */}

          <Route
            path="/"
            element={
              <Navigate
                to="/dashboard"
                replace
              />
            }
          />

          <Route
            path="/dashboard"
            element={<DashboardPage />}
          />

          <Route
            element={
              <RoleProtectedRoute
                roles={["ADMIN", "INVESTIGATOR"]}
              />
            }
          >
            <Route
              path="/ai"
              element={<AIAnalysisPage />}
            />
          </Route>

          <Route
            element={
              <RoleProtectedRoute
                roles={["ADMIN"]}
              />
            }
          >
            <Route path="/users" element={<UserAdminPage />} />
            <Route path="/audit" element={<AuditLogsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>

          {/* =================================================
              READ ACCESS
          ================================================= */}

          <Route
            element={
              <RoleProtectedRoute
                roles={[
                  "ADMIN",
                  "INVESTIGATOR",
                  "REVIEWER",
                ]}
              />
            }
          >
            {/* -------------------------
                CASES
            ------------------------- */}

            <Route
              path="/cases"
              element={<CaseListPage />}
            />

            <Route
              path="/cases/:id"
              element={<CaseDetailPage />}
            />

            {/* -------------------------
                DEVICES
            ------------------------- */}

            <Route
              path="/devices"
              element={<DeviceListPage />}
            />

            <Route
              path="/devices/:id"
              element={<DeviceDetailPage />}
            />

            {/* -------------------------
                EVIDENCE
            ------------------------- */}

            <Route
              path="/evidence"
              element={<EvidenceListPage />}
            />

            {/* -------------------------
                TIMELINE
            ------------------------- */}

            <Route
              path="/timeline"
              element={<TimelinePage />}
            />

            {/* -------------------------
                REPORT
            ------------------------- */}

            <Route
              path="/reports"
              element={<ReportsHubPage />}
            />

            <Route
              path="/reports/:deviceId"
              element={<ReportRoute />}
            />
          </Route>

          {/* =================================================
              CREATE / UPDATE ACCESS
          ================================================= */}

          <Route
            element={
              <RoleProtectedRoute
                roles={[
                  "ADMIN",
                  "INVESTIGATOR",
                ]}
              />
            }
          >
            {/* -------------------------
                CASES
            ------------------------- */}

            <Route
              path="/cases/new"
              element={<CaseCreatePage />}
            />

            <Route
              path="/cases/:id/edit"
              element={<CaseEditPage />}
            />

            {/* -------------------------
                DEVICES
            ------------------------- */}

            <Route
              path="/devices/new"
              element={<DeviceCreatePage />}
            />

            <Route
              path="/devices/:id/edit"
              element={<DeviceEditPage />}
            />
          </Route>

          {/* =================================================
              OTHER MODULES
          ================================================= */}

          {/* =================================================
              FALLBACK
          ================================================= */}

          <Route
            path="*"
            element={
              <Navigate
                to="/dashboard"
                replace
              />
            }
          />
        </Route>
      </Route>
    </Routes>
  );
}

/* ============================================================
   REPORT ROUTE

   URL:
   /reports/9

   ReportPage requires:
   deviceId: number

   useParams() gives us the device ID as a string,
   so we convert it to a number before rendering ReportPage.
============================================================ */

function ReportRoute() {
  const { deviceId } = useParams<{
    deviceId: string;
  }>();

  const id = Number(deviceId);

  if (
    !deviceId ||
    !Number.isFinite(id) ||
    id <= 0
  ) {
    return (
      <Navigate
        to="/dashboard"
        replace
      />
    );
  }

  return (
    <ReportPage
      deviceId={id}
    />
  );
}
