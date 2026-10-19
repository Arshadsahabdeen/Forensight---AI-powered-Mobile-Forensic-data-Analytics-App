package com.forensics.platform.service;

import com.forensics.platform.dto.InvestigationReportDto;
import com.forensics.platform.dto.InvestigationReportDto.*;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders a stored report snapshot to PDF. All text is escaped; the document is generated only from the
 * snapshot so repeated exports of the same report contain the same data.
 */
@Component
public class ReportPdfRenderer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private static final String WIN_ANSI_EXTRAS = "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ";

    public byte[] render(InvestigationReportDto r, String snapshotSha256, boolean snapshotVerified, String status,
                         String reviewedBy, Instant reviewedAt) {
        String html = html(r, snapshotSha256, snapshotVerified, status, reviewedBy, reviewedAt);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF rendering failed", ex);
        }
    }

    String html(InvestigationReportDto r, String sha, boolean verified, String status, String reviewedBy, Instant reviewedAt) {
        StringBuilder h = new StringBuilder(32_000);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>");
        h.append("<title>").append(e(r.title())).append("</title><style>")
                .append("@page { size: A4; margin: 18mm 15mm 20mm 15mm; ")
                .append("@bottom-left { content: '").append(css(r.reportNumber())).append(" | snapshot SHA-256 ").append(css(shortHash(sha))).append("'; font-size: 7pt; color: #64748b; } ")
                .append("@bottom-right { content: 'Page ' counter(page) ' of ' counter(pages); font-size: 7pt; color: #64748b; } }")
                .append("body { font-family: Helvetica, Arial, sans-serif; font-size: 9pt; color: #0f172a; line-height: 1.35; }")
                .append("h1 { font-size: 17pt; margin: 0 0 4pt 0; } h2 { font-size: 12pt; margin: 16pt 0 6pt 0; padding-bottom: 3pt; border-bottom: 1px solid #cbd5e1; color: #1e3a8a; }")
                .append("h3 { font-size: 10pt; margin: 10pt 0 4pt 0; } .muted { color: #475569; } .small { font-size: 7.5pt; }")
                .append("table { width: 100%; border-collapse: collapse; margin: 4pt 0 8pt 0; } th, td { border: 1px solid #cbd5e1; padding: 3pt 4pt; text-align: left; vertical-align: top; }")
                .append("th { background: #f1f5f9; font-weight: bold; } td.k { width: 30%; background: #f8fafc; font-weight: bold; }")
                .append(".mono { font-family: Courier, monospace; font-size: 7.5pt; word-wrap: break-word; } .box { border: 1px solid #cbd5e1; background: #f8fafc; padding: 6pt; margin: 6pt 0; }")
                .append(".warn { border-color: #f59e0b; background: #fffbeb; } .tag { font-size: 7pt; font-weight: bold; padding: 1pt 3pt; border: 1px solid #94a3b8; }")
                .append(".finding { border: 1px solid #cbd5e1; padding: 6pt; margin: 6pt 0; page-break-inside: avoid; }")
                .append("</style></head><body>");

        h.append("<h1>").append(e(r.title())).append("</h1>");
        h.append("<div class=\"muted\">Case ").append(e(r.caseDetails().caseNumber())).append(" | Report ").append(e(r.reportNumber())).append("</div>");
        table(h, new String[][]{
                {"Report number", r.reportNumber()},
                {"Generated", time(r.generatedAt()) + " by " + r.generatedBy()},
                {"Prepared by", r.preparedBy()},
                {"Report status", status + (reviewedAt == null ? " (not reviewed)" : " - reviewed by " + reviewedBy + " on " + time(reviewedAt))},
                {"Snapshot SHA-256", sha},
                {"Snapshot integrity", verified ? "Stored snapshot matches its SHA-256" : "WARNING: stored snapshot does NOT match its recorded SHA-256"},
                {"Schema", r.schemaVersion()}
        }, true);
        h.append("<div class=\"box").append(verified ? "" : " warn").append("\">").append(e(r.disclaimer())).append("</div>");

        CaseSection c = r.caseDetails();
        h.append("<h2>1. Case details</h2>");
        table(h, new String[][]{
                {"Case number", c.caseNumber()}, {"Title", c.title()}, {"Status / priority", c.status() + " / " + c.priority()},
                {"Category / type", c.category() + " / " + c.caseType()}, {"Lead investigator", c.leadInvestigator()},
                {"Location", c.location()}, {"Opened", time(c.createdAt())}, {"Description", c.description()}
        }, false);

        h.append("<h2>2. Devices</h2>");
        if (r.devices().isEmpty()) {
            h.append("<p class=\"muted\">No devices are registered to this case.</p>");
        } else {
            h.append("<table><tr><th>ID</th><th>Device</th><th>IMEI / serial</th><th>OS</th><th>Acquisition</th><th>Seized</th><th>Records</th></tr>");
            for (DeviceSection d : r.devices()) {
                h.append("<tr><td>").append(d.id()).append("</td><td>").append(e(d.deviceName())).append("<br/><span class=\"small muted\">")
                        .append(e(join(d.manufacturer(), d.model()))).append("</span></td><td class=\"mono\">").append(e(dash(d.imei())))
                        .append("<br/>").append(e(dash(d.serialNumber()))).append("</td><td>").append(e(join(d.operatingSystem(), d.osVersion())))
                        .append("</td><td>").append(e(join(d.acquisitionMethod(), d.acquisitionStatus()))).append("</td><td>")
                        .append(e(d.seizedDate() == null ? "-" : d.seizedDate().toString())).append("</td><td>").append(d.evidenceCount()).append("</td></tr>");
            }
            h.append("</table>");
        }

        h.append("<h2>3. Evidence sources and integrity</h2>");
        if (r.evidenceSources().isEmpty()) {
            h.append("<p class=\"muted\">No evidence files have been imported for this case.</p>");
        } else {
            for (SourceFileSection f : r.evidenceSources()) {
                h.append("<h3>File #").append(f.id()).append(": ").append(e(f.originalFileName())).append("</h3>");
                table(h, new String[][]{
                        {"SHA-256 (at import)", f.sha256()},
                        {"Format / size", f.sourceFormat() + " / " + f.sizeBytes() + " bytes"},
                        {"Imported", time(f.importedAt()) + " by " + f.importedBy()},
                        {"Device", dash(f.deviceName())},
                        {"Processing", f.processingStatus() + " - " + f.recordsExtracted() + " record(s), " + f.duplicatesSkipped()
                                + " duplicate(s) skipped, " + f.timestampIssues() + " timestamp issue(s)"},
                        {"Integrity", f.integrityStatus() + (f.lastVerifiedAt() == null ? " (never re-verified)" : " - last verified " + time(f.lastVerifiedAt()) + " by " + f.lastVerifiedBy())}
                }, true);
            }
        }

        EvidenceOverview o = r.evidenceOverview();
        h.append("<h2>4. Evidence overview</h2>");
        table(h, new String[][]{
                {"Total records", String.valueOf(o.totalRecords())},
                {"Time span (UTC)", o.firstEventTime() == null ? "-" : time(o.firstEventTime()) + " to " + time(o.lastEventTime())},
                {"Undated records", String.valueOf(o.undatedRecords())},
                {"Records without source file", String.valueOf(o.recordsWithoutSourceFile())},
                {"Records by type", mapText(o.recordsByType())},
                {"Timestamp derivation", mapText(o.recordsByTimestampStatus())}
        }, false);

        h.append("<h2>5. Findings (chronological narrative)</h2>");
        if (r.findings().isEmpty()) {
            h.append("<p class=\"muted\">No findings were recorded.</p>");
        }
        int index = 1;
        for (FindingSection f : r.findings()) {
            boolean unreviewedAi = f.aiGenerated() && !"REVIEWED".equals(f.reviewStatus());
            h.append("<div class=\"finding").append(unreviewedAi ? " warn" : "").append("\">");
            h.append("<h3>5.").append(index++).append(" ").append(e(f.title())).append("</h3>");
            h.append("<div class=\"small\"><span class=\"tag\">").append(e(basisLabel(f.basis()))).append("</span> <span class=\"tag\">")
                    .append(e(f.significance())).append(" significance</span> <span class=\"tag\">").append(e(f.reviewStatus())).append("</span>");
            if (f.aiGenerated()) {
                h.append(" <span class=\"tag\">AI-ASSISTED").append(f.aiEngine() == null ? "" : " (" + e(f.aiEngine()) + ")").append("</span>");
            }
            h.append("</div>");
            if (unreviewedAi) {
                h.append("<p><b>Not reviewed:</b> AI-assisted content shown for transparency; it has not been verified by a reviewer.</p>");
            }
            h.append("<p>").append(multiline(f.description())).append("</p>");
            h.append("<div class=\"small muted\">Recorded by ").append(e(f.createdBy()));
            if (f.reviewedBy() != null) {
                h.append("; ").append(e(f.reviewStatus().toLowerCase())).append(" by ").append(e(f.reviewedBy())).append(" on ").append(e(time(f.reviewedAt())));
                if (f.reviewComment() != null) {
                    h.append(" (\"").append(e(f.reviewComment())).append("\")");
                }
            }
            h.append("</div>");
            if (!f.evidence().isEmpty()) {
                evidenceTable(h, f.evidence());
            } else {
                h.append("<p class=\"small\"><b>No supporting evidence records are linked to this finding.</b></p>");
            }
            h.append("</div>");
        }

        h.append("<h2>6. ").append(r.fullTimelineIncluded() ? "Timeline (all records)" : "Key events (cited or bookmarked records)").append("</h2>");
        if (r.keyEvents().isEmpty()) {
            h.append("<p class=\"muted\">No events selected.</p>");
        } else {
            h.append("<table><tr><th>Time (UTC)</th><th>Original</th><th>Record</th><th>Details</th><th>Flags</th></tr>");
            for (EventEntry ev : r.keyEvents()) {
                EvidenceRef x = ev.evidence();
                h.append("<tr><td>").append(e(ev.eventTime() == null ? "undated" : time(ev.eventTime()))).append("<br/><span class=\"small muted\">")
                        .append(e(ev.timestampStatus())).append("</span></td><td class=\"small\">").append(e(dash(x.originalTimestamp())))
                        .append(x.originalTimezone() == null ? "" : "<br/>" + e(x.originalTimezone())).append("</td><td>#").append(x.evidenceId())
                        .append(" ").append(e(x.artifactType())).append("<br/><span class=\"small muted\">").append(e(dash(x.deviceName()))).append("</span></td><td>")
                        .append(e(dash(x.summary()))).append("<br/><span class=\"small muted\">").append(e(x.participants())).append("</span></td><td class=\"small\">")
                        .append(e(dash(ev.flags()))).append("</td></tr>");
            }
            h.append("</table>");
        }

        h.append("<h2>7. Methodology</h2><ol>");
        r.methodology().forEach(item -> h.append("<li>").append(e(item)).append("</li>"));
        h.append("</ol>");

        h.append("<h2>8. Limitations</h2><ul>");
        r.limitations().forEach(item -> h.append("<li>").append(e(item)).append("</li>"));
        h.append("</ul>");

        h.append("<h2>9. Audit information</h2>");
        AuditChainStatus chain = r.auditChain();
        h.append("<p>Audit-log hash chain at generation: <b>").append(e(chain.status())).append("</b> (").append(chain.recordsChecked())
                .append(" chained record(s) verified, ").append(chain.legacyUnchainedRecords()).append(" older unchained record(s); checked ")
                .append(e(time(chain.verifiedAt()))).append(").</p>");
        if (r.auditTrail().isEmpty()) {
            h.append("<p class=\"muted\">Case audit trail not included.</p>");
        } else {
            h.append("<table><tr><th>#</th><th>Time (UTC)</th><th>Actor</th><th>Action</th><th>Description</th></tr>");
            for (AuditEntry a : r.auditTrail()) {
                h.append("<tr><td>").append(a.id()).append("</td><td class=\"small\">").append(e(time(a.timestamp()))).append("</td><td>")
                        .append(e(dash(a.actor()))).append("</td><td class=\"small\">").append(e(a.action())).append(a.success() ? "" : " (failed)")
                        .append("</td><td class=\"small\">").append(e(a.description())).append("</td></tr>");
            }
            h.append("</table>");
        }
        h.append("</body></html>");
        return h.toString();
    }

    private void evidenceTable(StringBuilder h, List<EvidenceRef> refs) {
        h.append("<table class=\"small\"><tr><th>Record</th><th>Time (UTC) / original</th><th>Content</th><th>Source</th></tr>");
        for (EvidenceRef x : refs) {
            h.append("<tr><td>#").append(x.evidenceId()).append(" ").append(e(x.artifactType())).append("</td><td>")
                    .append(e(x.eventTime() == null ? "undated" : time(x.eventTime()))).append("<br/>").append(e(dash(x.originalTimestamp())))
                    .append(" [").append(e(x.timestampStatus())).append("]</td><td>").append(e(dash(x.summary()))).append("<br/><span class=\"muted\">")
                    .append(e(x.participants())).append("</span></td><td>").append(x.sourceFileId() == null ? "no source file" : "file #" + x.sourceFileId())
                    .append("<br/><span class=\"mono\">").append(e(dash(x.sourceRecordRef()))).append("</span></td></tr>");
        }
        h.append("</table>");
    }

    private void table(StringBuilder h, String[][] rows, boolean monoValues) {
        h.append("<table>");
        for (String[] row : rows) {
            boolean mono = monoValues && row[1] != null && row[1].matches("^[0-9a-f]{64}$");
            h.append("<tr><td class=\"k\">").append(e(row[0])).append("</td><td").append(mono ? " class=\"mono\"" : "").append(">")
                    .append(multiline(dash(row[1]))).append("</td></tr>");
        }
        h.append("</table>");
    }

    private static String basisLabel(String basis) {
        return switch (basis) {
            case "VERIFIED_FACT" -> "VERIFIED FACT";
            case "INFERENCE" -> "INFERENCE";
            case "AI_SUGGESTED" -> "AI-SUGGESTED";
            default -> basis;
        };
    }

    private static String mapText(Map<String, Long> map) {
        if (map == null || map.isEmpty()) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        map.forEach((key, value) -> text.append(text.isEmpty() ? "" : ", ").append(key).append(": ").append(value));
        return text.toString();
    }

    private static String time(Instant instant) {
        return instant == null ? "-" : TIME.format(instant);
    }

    private static String join(String a, String b) {
        String left = a == null ? "" : a;
        String right = b == null ? "" : b;
        String joined = (left + " " + right).trim();
        return joined.isEmpty() ? "-" : joined;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String shortHash(String sha) {
        return sha == null ? "-" : sha.substring(0, Math.min(16, sha.length())) + "...";
    }

    private static String multiline(String value) {
        return e(value).replace("\n", "<br/>");
    }

    /** XML-escape and restrict to characters the standard PDF fonts can draw. */
    static String e(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == '\n') {
                out.append('\n');
            } else if (cp < 0x20 || (cp >= 0x7F && cp < 0xA0)) {
                out.append(' ');
            } else if (cp == '&') {
                out.append("&amp;");
            } else if (cp == '<') {
                out.append("&lt;");
            } else if (cp == '>') {
                out.append("&gt;");
            } else if (cp == '"') {
                out.append("&quot;");
            } else if (cp == 0x2014 || cp == 0x2013 || cp == 0x2012 || cp == 0xB7 || cp == 0x2022) {
                out.append('-');
            } else if (cp == 0x2018 || cp == 0x2019) {
                out.append('\'');
            } else if (cp == 0x201C || cp == 0x201D) {
                out.append("&quot;");
            } else if (cp == 0x2026) {
                out.append("...");
            } else if (cp <= 0xFF || WIN_ANSI_EXTRAS.indexOf(cp) >= 0) {
                out.appendCodePoint(cp);
            } else if (cp == 0x2192) {
                out.append("-&gt;");
            } else if (cp == 0x2248) {
                out.append('~');
            } else {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static String css(String value) {
        return e(value).replace("'", "");
    }
}
