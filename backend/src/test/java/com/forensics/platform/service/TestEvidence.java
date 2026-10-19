package com.forensics.platform.service;

import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.InvestigationCase;
import com.forensics.platform.entity.TimestampStatus;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory evidence builders for pure unit tests. */
final class TestEvidence {
    private static final AtomicLong IDS = new AtomicLong(1000);

    private TestEvidence() {
    }

    static InvestigationCase investigationCase(long id) {
        InvestigationCase c = new InvestigationCase();
        c.setId(id);
        c.setCaseNumber("TEST-" + id);
        c.setLeadInvestigator("Lead");
        return c;
    }

    static Device device(long id, InvestigationCase c, String name) {
        Device d = new Device();
        d.setId(id);
        d.setDeviceName(name);
        d.setInvestigationCase(c);
        return d;
    }

    static Evidence sms(Device device, String from, String to, String text, String isoTime) {
        Evidence e = base(device, "SMS", isoTime);
        e.setSource(from);
        e.setTarget(to);
        e.setContent(text);
        return e;
    }

    static Evidence call(Device device, String from, String to, String title, String isoTime) {
        Evidence e = base(device, "CALL", isoTime);
        e.setSource(from);
        e.setTarget(to);
        e.setTitle(title);
        return e;
    }

    static Evidence contact(Device device, String name, String phone) {
        Evidence e = base(device, "CONTACT", null);
        e.setContactName(name);
        e.setPhoneNumber(phone);
        return e;
    }

    static Evidence location(Device device, String place, String isoTime) {
        Evidence e = base(device, "LOCATION", isoTime);
        e.setLocation(place);
        return e;
    }

    static Evidence base(Device device, String type, String isoTime) {
        Evidence e = new Evidence();
        e.setId(IDS.incrementAndGet());
        e.setDevice(device);
        e.setArtifactType(type);
        e.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        if (isoTime != null) {
            e.setEventTime(Instant.parse(isoTime));
            e.setOriginalTimestamp(isoTime);
            e.setTimestampStatus(TimestampStatus.EXACT);
        } else {
            e.setTimestampStatus(TimestampStatus.MISSING);
        }
        return e;
    }
}
