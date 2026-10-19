package com.forensics.platform.service;

import com.forensics.platform.dto.TimelineEventDto;
import com.forensics.platform.dto.TimelineEventDto.Flag;
import com.forensics.platform.dto.TimelineEventDto.TimelineResponse;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.NormalizedEvidenceCategory;
import com.forensics.platform.entity.TimestampStatus;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a case timeline from stored evidence records. Every event links to its evidence record;
 * records without a usable timestamp are returned separately instead of being dropped.
 */
@Service
public class TimelineService {

    static final Instant IMPLAUSIBLE_BEFORE = Instant.parse("2000-01-01T00:00:00Z");
    private static final Duration CORROBORATION_WINDOW = Duration.ofMinutes(2);
    private static final int MAX_UNDATED = 500;

    private final EvidenceRepository evidenceRepository;
    private final CaseAccessService caseAccessService;

    public TimelineService(EvidenceRepository evidenceRepository, CaseAccessService caseAccessService) {
        this.evidenceRepository = evidenceRepository;
        this.caseAccessService = caseAccessService;
    }

    public record TimelineQuery(Long caseId, Long deviceId, String entity, Set<String> types, String category,
                                Instant from, Instant to, boolean flaggedOnly, String order, int page, int size) {}

    @Transactional(readOnly = true)
    public TimelineResponse caseTimeline(TimelineQuery query, Authentication authentication) {
        caseAccessService.requireViewable(query.caseId(), authentication);
        if (query.page() < 0 || query.size() < 1 || query.size() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 500");
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
        NormalizedEvidenceCategory category = query.category() == null || query.category().isBlank()
                ? null : EvidenceCategories.parse(query.category());
        if (query.category() != null && !query.category().isBlank() && category == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category filter");
        }
        return build(evidenceRepository.findAllForCase(query.caseId()), query, category, Instant.now());
    }

    /**
     * Pure timeline construction (exposed for tests and report snapshots).
     */
    public static TimelineResponse build(List<Evidence> all, TimelineQuery query, NormalizedEvidenceCategory category, Instant now) {
        Map<Long, List<Flag>> flags = computeFlags(all, now);

        String entity = query.entity() == null || query.entity().isBlank() ? null : query.entity().trim().toLowerCase(Locale.ROOT);
        String entityPhone = entity == null ? null : CorrelationEngine.phoneKey(entity);
        Set<String> types = query.types() == null ? Set.of() : query.types().stream()
                .map(EvidenceCategories::normalizeType).collect(Collectors.toSet());

        List<Evidence> filtered = all.stream()
                .filter(e -> query.deviceId() == null || Objects.equals(e.getDevice().getId(), query.deviceId()))
                .filter(e -> types.isEmpty() || types.contains(EvidenceCategories.normalizeType(e.getArtifactType())))
                .filter(e -> category == null || EvidenceCategories.resolve(e.getArtifactType()) == category)
                .filter(e -> entity == null || matchesEntity(e, entity, entityPhone))
                .toList();

        List<Evidence> dated = filtered.stream()
                .filter(e -> e.getEventTime() != null)
                .filter(e -> query.from() == null || !e.getEventTime().isBefore(query.from()))
                .filter(e -> query.to() == null || !e.getEventTime().isAfter(query.to()))
                .filter(e -> !query.flaggedOnly() || !flags.getOrDefault(e.getId(), List.of()).isEmpty())
                .sorted(Comparator.comparing(Evidence::getEventTime).thenComparing(Evidence::getId))
                .collect(Collectors.toCollection(ArrayList::new));
        if ("desc".equalsIgnoreCase(query.order())) {
            Collections.reverse(dated);
        }
        // Undated records cannot be placed in a time range, so they are only shown when no range is set.
        List<Evidence> undated = query.from() != null || query.to() != null ? List.of() : filtered.stream()
                .filter(e -> e.getEventTime() == null)
                .sorted(Comparator.comparing(Evidence::getId))
                .toList();

        Map<TimestampStatus, Long> statusCounts = new EnumMap<>(TimestampStatus.class);
        for (Evidence e : filtered) {
            statusCounts.merge(status(e), 1L, Long::sum);
        }

        int from = Math.min(query.page() * query.size(), dated.size());
        int to = Math.min(from + query.size(), dated.size());
        List<TimelineEventDto> page = dated.subList(from, to).stream().map(e -> toDto(e, flags)).toList();
        Instant first = dated.stream().map(Evidence::getEventTime).min(Comparator.naturalOrder()).orElse(null);
        Instant last = dated.stream().map(Evidence::getEventTime).max(Comparator.naturalOrder()).orElse(null);
        long flagged = filtered.stream().filter(e -> !flags.getOrDefault(e.getId(), List.of()).isEmpty()).count();

        return new TimelineResponse(
                query.caseId(), query.deviceId(), query.page(), query.size(), dated.size(),
                (int) Math.ceil(dated.size() / (double) query.size()), undated.size(), first, last, statusCounts, flagged,
                page,
                undated.stream().limit(MAX_UNDATED).map(e -> toDto(e, flags)).toList(),
                "Event times are normalized to UTC. Original timestamp text and declared timezone are preserved per event. "
                        + "ASSUMED_UTC means the source gave no timezone; the true local offset is unknown."
        );
    }

    static Map<Long, List<Flag>> computeFlags(List<Evidence> all, Instant now) {
        Map<Long, List<Flag>> flags = new HashMap<>();
        for (Evidence e : all) {
            TimestampStatus status = status(e);
            Instant t = e.getEventTime();
            if (status == TimestampStatus.UNPARSEABLE) {
                add(flags, e, new Flag("UNPARSEABLE_TIMESTAMP", "WARNING",
                        "Source timestamp '" + e.getOriginalTimestamp() + "' could not be interpreted; event is undated.", List.of()));
            }
            if (status == TimestampStatus.MISSING) {
                add(flags, e, new Flag("MISSING_TIMESTAMP", "INFO", "Source record has no timestamp; event is undated.", List.of()));
            }
            if (status == TimestampStatus.ASSUMED_UTC) {
                add(flags, e, new Flag("ASSUMED_UTC", "INFO",
                        "Source timestamp had no timezone and was interpreted as UTC; actual local time may differ.", List.of()));
            }
            if (status == TimestampStatus.DATE_ONLY) {
                add(flags, e, new Flag("DATE_ONLY", "INFO", "Source gave a date without a time; placed at 00:00.", List.of()));
            }
            if (t == null) {
                continue;
            }
            Instant reference = e.getCreatedAt() != null && e.getCreatedAt().isBefore(now) ? e.getCreatedAt() : now;
            if (t.isAfter(reference.plus(Duration.ofMinutes(5)))) {
                add(flags, e, new Flag("FUTURE_TIMESTAMP", "WARNING",
                        "Event time is later than the time the record was imported; the device clock or timezone may be wrong.", List.of()));
            }
            if (t.isBefore(IMPLAUSIBLE_BEFORE)) {
                add(flags, e, new Flag("IMPLAUSIBLE_DATE", "WARNING",
                        "Event time is before 2000-01-01, which often indicates a reset device clock or an epoch default.", List.of()));
            }
            if (e.getDevice() != null && e.getDevice().getSeizedDate() != null
                    && t.isAfter(e.getDevice().getSeizedDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))) {
                add(flags, e, new Flag("AFTER_SEIZURE", "WARNING",
                        "Event time is after the device's recorded seizure date (" + e.getDevice().getSeizedDate() + ").", List.of()));
            }
        }

        // Same communication on different devices: corroborating if times agree, conflicting otherwise.
        Map<String, List<Evidence>> groups = new HashMap<>();
        for (Evidence e : all) {
            String key = CorrelationEngine.corroborationKey(e);
            if (key != null) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
        }
        for (List<Evidence> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (Evidence e : group) {
                List<Long> agree = new ArrayList<>();
                List<Long> conflict = new ArrayList<>();
                long maxGapMinutes = 0;
                for (Evidence other : group) {
                    if (other == e || Objects.equals(other.getDevice().getId(), e.getDevice().getId())) {
                        continue;
                    }
                    Duration gap = Duration.between(e.getEventTime(), other.getEventTime()).abs();
                    if (gap.compareTo(CORROBORATION_WINDOW) <= 0) {
                        agree.add(other.getId());
                    } else if (gap.compareTo(Duration.ofDays(2)) <= 0) {
                        conflict.add(other.getId());
                        maxGapMinutes = Math.max(maxGapMinutes, gap.toMinutes());
                    }
                }
                if (!agree.isEmpty()) {
                    add(flags, e, new Flag("CORROBORATED", "INFO",
                            "The same communication appears on another device at a matching time.", agree));
                }
                if (!conflict.isEmpty()) {
                    add(flags, e, new Flag("CONFLICTING_TIMESTAMP", "WARNING",
                            "The same communication appears on another device with a time difference of up to " + maxGapMinutes
                                    + " minute(s). Check device clocks and timezones.", conflict));
                }
            }
        }
        return flags;
    }

    private static boolean matchesEntity(Evidence e, String entity, String entityPhone) {
        for (String value : new String[]{e.getSource(), e.getTarget(), e.getPhoneNumber(), e.getContactName(), e.getLocation()}) {
            if (value == null) {
                continue;
            }
            if (value.toLowerCase(Locale.ROOT).contains(entity)) {
                return true;
            }
            if (entityPhone != null && entityPhone.equals(CorrelationEngine.phoneKey(value))) {
                return true;
            }
        }
        return false;
    }

    private static TimestampStatus status(Evidence e) {
        if (e.getTimestampStatus() != null) {
            return e.getTimestampStatus();
        }
        return e.getEventTime() == null ? TimestampStatus.MISSING : TimestampStatus.LEGACY_IMPORT;
    }

    private static void add(Map<Long, List<Flag>> flags, Evidence e, Flag flag) {
        flags.computeIfAbsent(e.getId(), k -> new ArrayList<>()).add(flag);
    }

    static TimelineEventDto toDto(Evidence e, Map<Long, List<Flag>> flags) {
        String content = e.getContent();
        String summary = content == null ? null : content.length() > 280 ? content.substring(0, 277) + "..." : content;
        return new TimelineEventDto(
                e.getId(),
                e.getArtifactType(),
                EvidenceCategories.resolve(e.getArtifactType()),
                e.getTitle(),
                summary,
                e.getSource(),
                e.getTarget(),
                e.getPhoneNumber(),
                e.getContactName(),
                e.getLocation(),
                e.getLatitude(),
                e.getLongitude(),
                e.getEventTime(),
                e.getOriginalTimestamp(),
                e.getOriginalTimezone(),
                status(e),
                e.getDevice() == null ? null : e.getDevice().getId(),
                e.getDevice() == null ? null : e.getDevice().getDeviceName(),
                e.getDevice() == null || e.getDevice().getInvestigationCase() == null ? null : e.getDevice().getInvestigationCase().getId(),
                e.getSourceFile() == null ? null : e.getSourceFile().getId(),
                e.getSourceRecordRef(),
                flags.getOrDefault(e.getId(), List.of())
        );
    }
}
