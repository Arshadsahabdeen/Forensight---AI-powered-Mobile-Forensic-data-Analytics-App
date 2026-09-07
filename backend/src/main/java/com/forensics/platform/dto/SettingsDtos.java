package com.forensics.platform.dto;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record PlatformSettingsResponse(int auditRetentionDays, int sessionTimeoutMinutes, boolean loginAlertsEnabled, boolean maintenanceMode, int defaultPageSize) {}
    public record PlatformSettingsRequest(int auditRetentionDays, int sessionTimeoutMinutes, boolean loginAlertsEnabled, boolean maintenanceMode, int defaultPageSize) {}
}