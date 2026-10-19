package com.forensics.platform.ufdr;

import com.forensics.platform.entity.TimestampStatus;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Converts source timestamps to UTC instants while recording exactly how the conversion was made.
 *
 * <p>Only unambiguous formats are accepted: ISO-8601 (with or without offset/zone), year-first
 * date-times, ISO dates, compact {@code yyyyMMdd} and Unix epoch seconds/milliseconds.
 * Day/month-ambiguous formats such as {@code 01/08/2026} are deliberately reported as
 * {@link TimestampStatus#UNPARSEABLE} instead of guessed. The original text is always kept.</p>
 */
public final class TimestampParser {

    public record ParsedTimestamp(Instant instant, TimestampStatus status, String originalText, String originalTimezone) {
        public boolean hasInstant() {
            return instant != null;
        }
    }

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            yearFirst("yyyy-MM-dd HH:mm"),
            yearFirst("yyyy/MM/dd HH:mm"),
            yearFirst("yyyy.MM.dd HH:mm")
    );

    private static final DateTimeFormatter OFFSET_SPACE_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendOffset("+HH:MM", "Z")
            .toFormatter();

    private static final DateTimeFormatter OFFSET_SPACE_COMPACT_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendOffset("+HHMM", "Z")
            .toFormatter();

    private TimestampParser() {
    }

    public static ParsedTimestamp parse(String raw) {
        return parse(raw, null);
    }

    /**
     * @param raw          timestamp text from the source record
     * @param declaredZone optional zone/offset declared alongside the timestamp (e.g. {@code +05:30},
     *                     {@code Asia/Kolkata}); applied only when the timestamp itself has no offset
     */
    public static ParsedTimestamp parse(String raw, String declaredZone) {
        String text = raw == null ? null : raw.trim();
        String zoneText = declaredZone == null || declaredZone.isBlank() ? null : declaredZone.trim();
        if (text == null || text.isEmpty()) {
            return new ParsedTimestamp(null, TimestampStatus.MISSING, null, zoneText);
        }

        if (DIGITS.matcher(text).matches()) {
            return parseDigits(text, zoneText);
        }

        ParsedTimestamp withOffset = parseWithExplicitZone(text);
        if (withOffset != null) {
            return withOffset;
        }

        ZoneId declared = toZone(zoneText);

        for (DateTimeFormatter format : LOCAL_DATE_TIME_FORMATS) {
            try {
                LocalDateTime local = LocalDateTime.parse(text, format);
                return local(local, text, zoneText, declared, false);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }

        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            return local(date.atStartOfDay(), text, zoneText, declared, true);
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        return new ParsedTimestamp(null, TimestampStatus.UNPARSEABLE, text, zoneText);
    }

    private static ParsedTimestamp parseDigits(String text, String zoneText) {
        try {
            if (text.length() == 13) {
                return new ParsedTimestamp(Instant.ofEpochMilli(Long.parseLong(text)), TimestampStatus.EXACT, text, "UTC (Unix epoch ms)");
            }
            if (text.length() == 10) {
                return new ParsedTimestamp(Instant.ofEpochSecond(Long.parseLong(text)), TimestampStatus.EXACT, text, "UTC (Unix epoch s)");
            }
            if (text.length() == 8) {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
                return local(date.atStartOfDay(), text, zoneText, toZone(zoneText), true);
            }
        } catch (NumberFormatException | DateTimeException ignored) {
            // fall through
        }
        return new ParsedTimestamp(null, TimestampStatus.UNPARSEABLE, text, zoneText);
    }

    private static ParsedTimestamp parseWithExplicitZone(String text) {
        try {
            ZonedDateTime zoned = ZonedDateTime.parse(text, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            return new ParsedTimestamp(zoned.toInstant(), TimestampStatus.EXACT, text, zoneLabel(zoned));
        } catch (DateTimeParseException ignored) {
            // not ISO zoned
        }
        for (DateTimeFormatter format : List.of(OFFSET_SPACE_FORMAT, OFFSET_SPACE_COMPACT_FORMAT)) {
            try {
                OffsetDateTime offset = OffsetDateTime.parse(text, format);
                return new ParsedTimestamp(offset.toInstant(), TimestampStatus.EXACT, text, offsetLabel(offset.getOffset()));
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static ParsedTimestamp local(LocalDateTime local, String text, String zoneText, ZoneId declared, boolean dateOnly) {
        if (declared != null) {
            Instant instant = local.atZone(declared).toInstant();
            return new ParsedTimestamp(instant, dateOnly ? TimestampStatus.DATE_ONLY : TimestampStatus.EXACT, text, zoneText);
        }
        Instant instant = local.toInstant(ZoneOffset.UTC);
        return new ParsedTimestamp(instant, dateOnly ? TimestampStatus.DATE_ONLY : TimestampStatus.ASSUMED_UTC, text, null);
    }

    private static ZoneId toZone(String zoneText) {
        if (zoneText == null) {
            return null;
        }
        try {
            if (zoneText.equalsIgnoreCase("UTC") || zoneText.equalsIgnoreCase("GMT") || zoneText.equals("Z")) {
                return ZoneOffset.UTC;
            }
            return ZoneId.of(zoneText);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private static String zoneLabel(ZonedDateTime zoned) {
        if (zoned.getZone() instanceof ZoneOffset offset) {
            return offsetLabel(offset);
        }
        return zoned.getZone().getId() + " (" + offsetLabel(zoned.getOffset()) + ")";
    }

    private static String offsetLabel(ZoneOffset offset) {
        return offset.equals(ZoneOffset.UTC) ? "UTC" : offset.getId();
    }

    private static DateTimeFormatter yearFirst(String base) {
        return new DateTimeFormatterBuilder()
                .appendPattern(base)
                .optionalStart().appendPattern(":ss").optionalEnd()
                .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                .toFormatter();
    }
}
