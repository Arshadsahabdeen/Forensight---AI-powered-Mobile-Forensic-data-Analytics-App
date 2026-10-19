package com.forensics.platform.ufdr;

import com.forensics.platform.entity.TimestampStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceExtractorTest {

    static Path sample(String name) {
        return Path.of("..", "datasets", "samples", name);
    }

    private static EvidenceExtractor.ExtractionResult extract(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return EvidenceExtractor.extract(XMLParser.parse(in));
        }
    }

    @Test
    void basicSampleYieldsExactlyTheFiveRecordsInTheFile() throws Exception {
        var result = extract(sample("sample-basic.xml"));

        // Regression: <message> body elements inside <sms> must not become extra (empty) SMS records.
        assertThat(result.records()).hasSize(5);
        assertThat(result.records()).extracting(EvidenceExtractor.ExtractedRecord::artifactType)
                .containsExactly("SMS", "SMS", "CONTACT", "CALL", "LOCATION");
        assertThat(result.records()).allSatisfy(record -> assertThat(record.sourceRecordRef()).startsWith("/UFDR/"));

        var firstSms = result.records().get(0);
        assertThat(firstSms.source()).isEqualTo("9876543210");
        assertThat(firstSms.target()).isEqualTo("9123456780");
        assertThat(firstSms.content()).isEqualTo("Where are you?");
        assertThat(firstSms.timestamp().instant()).isEqualTo(Instant.parse("2026-08-01T10:30:00Z"));
        assertThat(firstSms.sourceRecordRef()).isEqualTo("/UFDR/sms[1]");
        assertThat(result.records().get(1).sourceRecordRef()).isEqualTo("/UFDR/sms[2]");

        var contact = result.records().get(2);
        assertThat(contact.contactName()).isEqualTo("John Doe");
        assertThat(contact.phoneNumber()).isEqualTo("9876543210");

        var location = result.records().get(4);
        assertThat(location.latitude()).isEqualTo(11.0168);
        assertThat(location.location()).isEqualTo("Coimbatore");

        assertThat(result.device().imei()).isEqualTo("123456789012345");
        assertThat(result.device().model()).isEqualTo("Galaxy S24 Ultra");
    }

    @Test
    void timestampVariantsAndNestedMediaLocationAreHandled() throws Exception {
        var result = extract(sample("device-a-android.xml"));

        var statuses = result.records().stream().map(r -> r.timestamp().status()).toList();
        assertThat(statuses).contains(TimestampStatus.EXACT, TimestampStatus.ASSUMED_UTC, TimestampStatus.UNPARSEABLE, TimestampStatus.MISSING);

        var photo = result.records().stream().filter(r -> r.artifactType().equals("PHOTO")).findFirst().orElseThrow();
        assertThat(photo.title()).isEqualTo("IMG_20260714_1102.jpg");
        assertThat(photo.latitude()).isEqualTo(12.9719);
        // The photo's nested <location> must not be extracted as a separate LOCATION record.
        assertThat(result.records().stream().filter(r -> r.artifactType().equals("LOCATION"))).hasSize(1);

        var call = result.records().stream().filter(r -> r.artifactType().equals("CALL")).findFirst().orElseThrow();
        assertThat(call.title()).isEqualTo("Outgoing call (184 s)");
        assertThat(call.timestamp().instant()).isEqualTo(Instant.parse("2026-07-14T05:10:00Z"));
    }

    @Test
    void doctypeAndExternalEntitiesAreRejected() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <UFDR><sms><message>&xxe;</message></sms></UFDR>
                """;
        assertThatThrownBy(() -> XMLParser.parse(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(EvidenceFormatException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void malformedXmlReportsLocationWithoutContent() {
        String broken = "<UFDR><sms><message>secret text</sms></UFDR>";
        assertThatThrownBy(() -> XMLParser.parse(new ByteArrayInputStream(broken.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(EvidenceFormatException.class)
                .hasMessageStartingWith("Malformed XML at line")
                .hasMessageNotContaining("secret");
    }

    @Test
    void formatDetectionChecksContentNotJustExtension() throws Exception {
        byte[] xml = "<?xml version=\"1.0\"?><UFDR/>".getBytes(StandardCharsets.UTF_8);
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        assertThat(UFDRParser.detectFormat("a.xml", xml)).isNotNull();
        assertThat(UFDRParser.detectFormat("a.ufdr", zip)).isNotNull();
        assertThatThrownBy(() -> UFDRParser.detectFormat("a.xml", zip)).isInstanceOf(EvidenceFormatException.class);
        assertThatThrownBy(() -> UFDRParser.detectFormat("a.zip", xml)).isInstanceOf(EvidenceFormatException.class);
        assertThatThrownBy(() -> UFDRParser.detectFormat("a.exe", xml)).isInstanceOf(EvidenceFormatException.class);
    }
}
