package com.forensics.platform.service;

import com.forensics.platform.dto.SettingsDtos;
import com.forensics.platform.entity.PlatformSetting;
import com.forensics.platform.repository.PlatformSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Administrator-controlled settings that the platform enforces:
 * <ul>
 *   <li>{@code sessionTimeoutMinutes}: idle limit. The browser signs out after this much inactivity, and the
 *       server refuses to refresh a session idle for longer (never shorter than the access-token lifetime).</li>
 *   <li>{@code maintenanceMode}: non-administrators cannot make changes (HTTP 503) while enabled.</li>
 *   <li>{@code defaultPageSize}: default list size in the user interface.</li>
 *   <li>{@code auditRetentionDays} and {@code loginAlertsEnabled}: recorded policy values. Audit records are
 *       append-only and are never deleted automatically; all sign-in events are always audited.</li>
 * </ul>
 */
@Service
public class PlatformSettingsService {

    private final PlatformSettingRepository repository;
    private final long accessTokenMinutes;

    public PlatformSettingsService(PlatformSettingRepository repository,
                                   @Value("${app.security.jwt-access-expiration-ms:900000}") long accessExpirationMs) {
        this.repository = repository;
        this.accessTokenMinutes = Math.max(1, (long) Math.ceil(accessExpirationMs / 60000.0));
    }

    @Transactional(readOnly = true)
    public SettingsDtos.PlatformSettingsResponse get() {
        return new SettingsDtos.PlatformSettingsResponse(
                intSetting("auditRetentionDays", 365),
                intSetting("sessionTimeoutMinutes", 30),
                boolSetting("loginAlertsEnabled", true),
                boolSetting("maintenanceMode", false),
                intSetting("defaultPageSize", 25));
    }

    @Transactional
    public SettingsDtos.PlatformSettingsResponse update(SettingsDtos.PlatformSettingsRequest request) {
        if (request.auditRetentionDays() < 30 || request.auditRetentionDays() > 3650) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit retention must be between 30 and 3650 days");
        }
        if (request.sessionTimeoutMinutes() < 5 || request.sessionTimeoutMinutes() > 1440) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session timeout must be between 5 and 1440 minutes");
        }
        if (request.defaultPageSize() < 10 || request.defaultPageSize() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default page size must be between 10 and 100");
        }
        save("auditRetentionDays", request.auditRetentionDays());
        save("sessionTimeoutMinutes", request.sessionTimeoutMinutes());
        save("loginAlertsEnabled", request.loginAlertsEnabled());
        save("maintenanceMode", request.maintenanceMode());
        save("defaultPageSize", request.defaultPageSize());
        return get();
    }

    public boolean maintenanceMode() {
        return boolSetting("maintenanceMode", false);
    }

    /** Server-side idle limit for refreshing a session, never shorter than the access-token lifetime. */
    public long effectiveIdleTimeoutMinutes() {
        return Math.max(intSetting("sessionTimeoutMinutes", 30), accessTokenMinutes);
    }

    public long accessTokenMinutes() {
        return accessTokenMinutes;
    }

    private int intSetting(String key, int fallback) {
        try {
            return repository.findBySettingKey(key).map(PlatformSetting::getSettingValue).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean boolSetting(String key, boolean fallback) {
        return repository.findBySettingKey(key).map(PlatformSetting::getSettingValue).map(Boolean::parseBoolean).orElse(fallback);
    }

    private void save(String key, Object value) {
        repository.findBySettingKey(key).ifPresentOrElse(
                setting -> setting.setSettingValue(String.valueOf(value)),
                () -> repository.save(new PlatformSetting(key, String.valueOf(value))));
    }
}
