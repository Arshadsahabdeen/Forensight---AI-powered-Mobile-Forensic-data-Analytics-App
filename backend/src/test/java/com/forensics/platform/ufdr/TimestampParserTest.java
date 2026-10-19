package com.forensics.platform.ufdr;

import com.forensics.platform.entity.TimestampStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampParserTest {

    @Test
    void utcInstantIsExact() {
        var parsed = TimestampParser.parse("2026-08-01T10:30:00Z");
        assertThat(parsed.instant()).isEqualTo(Instant.parse("2026-08-01T10:30:00Z"));
        assertThat(parsed.status()).isEqualTo(TimestampStatus.EXACT);
        assertThat(parsed.originalTimezone()).isEqualTo("UTC");
        assertThat(parsed.originalText()).isEqualTo("2026-08-01T10:30:00Z");
    }

    @Test
    void offsetIsAppliedAndPreserved() {
        var parsed = TimestampParser.parse("2026-07-14T09:20:00+05:30");
        assertThat(parsed.instant()).isEqualTo(Instant.parse("2026-07-14T03:50:00Z"));
        assertThat(parsed.status()).isEqualTo(TimestampStatus.EXACT);
        assertThat(parsed.originalTimezone()).isEqualTo("+05:30");
    }

    @Test
    void spaceSeparatedOffsetIsSupported() {
        var parsed = TimestampParser.parse("2026-07-14 09:20:00 +0530");
        assertThat(parsed.instant()).isEqualTo(Instant.parse("2026-07-14T03:50:00Z"));
        assertThat(parsed.status()).isEqualTo(TimestampStatus.EXACT);
    }

    @Test
    void localTimeWithoutZoneIsAssumedUtcAndLabelled() {
        var parsed = TimestampParser.parse("2026-07-14 18:45:00");
        assertThat(parsed.instant()).isEqualTo(Instant.parse("2026-07-14T18:45:00Z"));
        assertThat(parsed.status()).isEqualTo(TimestampStatus.ASSUMED_UTC);
        assertThat(parsed.originalTimezone()).isNull();
    }

    @Test
    void declaredZoneIsUsedForLocalTimes() {
        var parsed = TimestampParser.parse("2026-07-14 09:20", "Asia/Kolkata");
        assertThat(parsed.instant()).isEqualTo(Instant.parse("2026-07-14T03:50:00Z"));
        assertThat(parsed.status()).isEqualTo(TimestampStatus.EXACT);
        assertThat(parsed.originalTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void epochMillisecondsAndSeconds() {
        assertThat(TimestampParser.parse("1784005800000").instant()).isEqualTo(Instant.parse("2026-07-14T05:10:00Z"));
        assertThat(TimestampParser.parse("1784005800").instant()).isEqualTo(Instant.parse("2026-07-14T05:10:00Z"));
        assertThat(TimestampParser.parse("1784005800").status()).isEqualTo(TimestampStatus.EXACT);
    }

    @Test
    void dateOnlyIsLabelled() {
        var parsed = TimestampParser.parse("2026-07-14");
        assertThat(parsed.instant()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(parsed.status()).isEqualTo(TimestampStatus.DATE_ONLY);
    }

    @Test
    void missingAndUnparseableKeepNoInstant() {
        assertThat(TimestampParser.parse(null).status()).isEqualTo(TimestampStatus.MISSING);
        assertThat(TimestampParser.parse("   ").status()).isEqualTo(TimestampStatus.MISSING);
        var garbage = TimestampParser.parse("not-recorded");
        assertThat(garbage.status()).isEqualTo(TimestampStatus.UNPARSEABLE);
        assertThat(garbage.instant()).isNull();
        assertThat(garbage.originalText()).isEqualTo("not-recorded");
    }

    @Test
    void ambiguousDayMonthFormatsAreNotGuessed() {
        var parsed = TimestampParser.parse("01/08/2026 10:30");
        assertThat(parsed.status()).isEqualTo(TimestampStatus.UNPARSEABLE);
        assertThat(parsed.instant()).isNull();
    }
}
