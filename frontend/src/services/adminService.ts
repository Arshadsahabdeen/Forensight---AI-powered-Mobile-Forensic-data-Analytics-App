import { api } from "./api";
import type { Role } from "../types/auth";

export interface AdminUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  roles: Role[];
  enabled: boolean;
  accountLocked: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuditLog {
  id: number;
  userId: number | null;
  username: string | null;
  action: string;
  timestamp: string;
  description: string;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
}

export async function getAdminUsers() {
  const { data } = await api.get<AdminUser[]>("/users");
  return data;
}

export async function getAuditLogs() {
  const { data } = await api.get<AuditLog[]>("/audit");
  return data;
}

export interface PlatformSettings {
  auditRetentionDays: number;
  sessionTimeoutMinutes: number;
  loginAlertsEnabled: boolean;
  maintenanceMode: boolean;
  defaultPageSize: number;
}

export async function updateUserStatus(id: number, enabled: boolean, accountLocked: boolean) {
  const { data } = await api.patch<AdminUser>(`/users/${id}/status`, { enabled, accountLocked });
  return data;
}

export async function updateUserRole(id: number, role: AdminUser["roles"][number]) {
  const { data } = await api.patch<AdminUser>(`/users/${id}/role`, { role });
  return data;
}

export async function getPlatformSettings() {
  const { data } = await api.get<PlatformSettings>("/users/settings");
  return data;
}

export async function updatePlatformSettings(settings: PlatformSettings) {
  const { data } = await api.patch<PlatformSettings>("/users/settings", settings);
  return data;
}
