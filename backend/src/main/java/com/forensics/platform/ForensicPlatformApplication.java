package com.forensics.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ForensicPlatformApplication {
    public static void main(String[] args) {
        // Several legacy columns are TIMESTAMP WITHOUT TIME ZONE. Running the JVM (and therefore the
        // JDBC session) in UTC makes Instant values round-trip identically on every host, so evidence
        // times and audit hashes do not depend on the server's local timezone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ForensicPlatformApplication.class, args);
    }
}
