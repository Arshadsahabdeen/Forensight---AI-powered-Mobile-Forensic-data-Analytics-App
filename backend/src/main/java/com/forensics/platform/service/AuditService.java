package com.forensics.platform.service;

import com.forensics.platform.entity.AuditLog;
import com.forensics.platform.entity.User;
import com.forensics.platform.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * Writes append-only audit records. Each record stores the SHA-256 of its own canonical content
 * chained to the previous record's hash, so any later edit, deletion or reordering of chained
 * records is detectable with {@link #verifyChain()}.
 *
 * <p>Records are written in their own transaction so that denied or failed operations are still
 * audited. Writes are serialized within this process to keep the chain linear; running several
 * backend instances against one database would require a database-level lock instead.</p>
 *
 * <p>Descriptions must never contain evidence content, passwords or tokens.</p>
 */
@Service
public class AuditService {

    private static final String NULL_MARKER = "␀";
    private static final char SEPARATOR = '\u001f';

    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditLogRepository auditLogRepository, PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Entry(
            Long userId,
            String username,
            String action,
            String description,
            boolean success,
            Long caseId,
            String entityType,
            String entityId,
            HttpServletRequest request
    ) {
        public static Entry of(String username, String action, String description) {
            return new Entry(null, username, action, description, true, null, null, null, null);
        }

        public Entry forCase(Long id) {
            return new Entry(userId, username, action, description, success, id, entityType, entityId, request);
        }

        public Entry entity(String type, Object id) {
            return new Entry(userId, username, action, description, success, caseId, type, id == null ? null : String.valueOf(id), request);
        }

        public Entry failed() {
            return new Entry(userId, username, action, description, false, caseId, entityType, entityId, request);
        }
    }

    public record ChainVerification(
            String status,
            long recordsChecked,
            long legacyUnchainedRecords,
            Long firstBrokenRecordId,
            String detail,
            Instant verifiedAt
    ) {}

    public void record(String username, String action, String description) {
        record(Entry.of(username, action, description));
    }

    public void record(User user, String action, String description, HttpServletRequest request, boolean success) {
        record(new Entry(
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                action, description, success, null, null, null, request
        ));
    }

    public void record(String username, String action, String description, HttpServletRequest request, boolean success) {
        record(new Entry(null, username, action, description, success, null, null, null, request));
    }

    public void record(Entry entry) {
        HttpServletRequest request = entry.request() != null ? entry.request() : currentRequest();
        AuditLog log = new AuditLog();
        log.setUserId(entry.userId());
        log.setUsername(truncate(entry.username(), 255));
        log.setAction(truncate(entry.action(), 255));
        log.setDescription(truncate(entry.description(), 1000));
        log.setSuccess(entry.success());
        log.setCaseId(entry.caseId());
        log.setEntityType(truncate(entry.entityType(), 60));
        log.setEntityId(truncate(entry.entityId(), 80));
        log.setIpAddress(truncate(clientIp(request), 255));
        log.setUserAgent(truncate(request == null ? null : request.getHeader("User-Agent"), 512));
        append(log);
    }

    private synchronized void append(AuditLog log) {
        transactionTemplate.executeWithoutResult(status -> {
            // Millisecond precision survives every supported database round trip unchanged.
            log.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            String previous = auditLogRepository.findTopByEntryHashIsNotNullOrderByIdDesc()
                    .map(AuditLog::getEntryHash)
                    .orElse(null);
            log.setPreviousHash(previous);
            log.setEntryHash(hash(log, previous));
            auditLogRepository.save(log);
        });
    }

    /**
     * Recomputes every chained record's hash and checks the links between consecutive records.
     * Records written before the chain existed (no hash) are counted but cannot be verified.
     */
    public ChainVerification verifyChain() {
        long checked = 0;
        long legacy = 0;
        String expectedPrevious = null;
        boolean chainStarted = false;
        long lastId = 0;

        while (true) {
            List<AuditLog> batch = auditLogRepository.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, 500));
            if (batch.isEmpty()) {
                break;
            }
            for (AuditLog log : batch) {
                lastId = log.getId();
                if (log.getEntryHash() == null) {
                    if (chainStarted) {
                        return broken(checked, legacy, log.getId(), "Record has no hash after the hash chain started.");
                    }
                    legacy++;
                    continue;
                }
                if (chainStarted && !equalsNullable(expectedPrevious, log.getPreviousHash())) {
                    return broken(checked, legacy, log.getId(), "Link to the previous record does not match (record removed, reordered or altered).");
                }
                String recomputed = hash(log, log.getPreviousHash());
                if (!recomputed.equals(log.getEntryHash())) {
                    return broken(checked, legacy, log.getId(), "Stored hash does not match the record content (record altered).");
                }
                chainStarted = true;
                expectedPrevious = log.getEntryHash();
                checked++;
            }
        }
        String detail = checked == 0
                ? "No hash-chained audit records exist yet."
                : "All " + checked + " chained audit records verified.";
        return new ChainVerification("INTACT", checked, legacy, null, detail, Instant.now());
    }

    static String hash(AuditLog log, String previousHash) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, previousHash);
        append(canonical, log.getTimestamp() == null ? null : String.valueOf(log.getTimestamp().toEpochMilli()));
        append(canonical, log.getUserId() == null ? null : String.valueOf(log.getUserId()));
        append(canonical, log.getUsername());
        append(canonical, log.getAction());
        append(canonical, log.getDescription());
        append(canonical, log.getIpAddress());
        append(canonical, log.getUserAgent());
        append(canonical, String.valueOf(log.isSuccess()));
        append(canonical, log.getCaseId() == null ? null : String.valueOf(log.getCaseId()));
        append(canonical, log.getEntityType());
        append(canonical, log.getEntityId());
        return sha256(canonical.toString());
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value == null ? NULL_MARKER : value).append(SEPARATOR);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ChainVerification broken(long checked, long legacy, Long id, String detail) {
        return new ChainVerification("BROKEN", checked, legacy, id, detail, Instant.now());
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
