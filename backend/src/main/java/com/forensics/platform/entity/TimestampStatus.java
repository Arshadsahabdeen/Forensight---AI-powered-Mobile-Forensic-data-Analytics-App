package com.forensics.platform.entity;

/**
 * How an evidence record's normalized UTC event time was derived from the source timestamp.
 */
public enum TimestampStatus {
    /** Source declared an explicit offset/zone (or epoch value); the UTC instant is exact. */
    EXACT,
    /** Source gave a local date-time without a zone; interpreted as UTC. The true offset is unknown. */
    ASSUMED_UTC,
    /** Source gave a date without a time; interpreted as 00:00 UTC on that date. */
    DATE_ONLY,
    /** No timestamp in the source record. */
    MISSING,
    /** A timestamp was present but could not be parsed; the original text is preserved. */
    UNPARSEABLE,
    /** Imported before original timestamps were preserved; derivation is unknown. */
    LEGACY_IMPORT
}
