package com.forensics.platform.config;

import com.forensics.platform.entity.*;
import com.forensics.platform.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(RoleRepository roleRepository, UserRepository userRepository, CaseRepository caseRepository, DeviceRepository deviceRepository, EvidenceRepository evidenceRepository, ReportRepository reportRepository, AuditLogRepository auditLogRepository, PasswordEncoder passwordEncoder, @Value("${app.security.seed-development-users:true}") boolean seedDevelopmentUsers) {
        return args -> {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN)));
            Role investigatorRole = roleRepository.findByName(RoleName.INVESTIGATOR).orElseGet(() -> roleRepository.save(new Role(RoleName.INVESTIGATOR)));
            Role reviewerRole = roleRepository.findByName(RoleName.REVIEWER).orElseGet(() -> roleRepository.save(new Role(RoleName.REVIEWER)));

            if (seedDevelopmentUsers) {
                seedUser(userRepository, passwordEncoder, "admin", "admin@forensics.local", "System Administrator", "Admin@123", Set.of(adminRole));
                seedUser(userRepository, passwordEncoder, "investigator", "investigator@forensics.local", "Aarav Sharma", "Investigator@123", Set.of(investigatorRole));
                seedUser(userRepository, passwordEncoder, "reviewer", "reviewer@forensics.local", "Maya Reviewer", "Reviewer@123", Set.of(reviewerRole));
                seedInvestigators(userRepository, passwordEncoder, investigatorRole);
            }

            if (caseRepository.count() == 0) {
                List<String> investigators = List.of(
                        "Aarav Sharma", "Maya Khan", "Jyoti Rao", "Nikhil Patel", "Aditi Mehra",
                        "Rahul Menon", "Priya Singh", "Karan Desai", "Neha Verma", "Siddharth Iyer"
                );
                List<String> caseTitles = List.of(
                        "Cross-border cryptocurrency wallet seizure", "Cloud backup tampering analysis", "Deleted chats in extortion complaint",
                        "Insider intellectual property theft", "Dark web escrow communication trace", "Mobile malware lateral movement review",
                        "SIM swap coordinated account takeover", "Emergency dispatch spoofing investigation", "Corporate espionage evidence chain validation",
                        "Unauthorized remote wipe reconstruction", "Hidden vault extraction from seized handset", "Financial mule account coordination",
                        "Deepfake media provenance verification", "Encrypted backup brute-force timeline", "Travel route geofence anomaly case",
                        "Unauthorized drone telemetry control", "Counterfeit identity ring device mapping", "Stolen enterprise credentials correlation",
                        "Confidential source leak attribution", "Messaging app metadata conflict review", "Incident bridge call recording forensics",
                        "Deleted browser wallet recovery", "Supply chain communication fraud", "Insider sabotage endpoint triage",
                        "Organized phishing kit attribution"
                );
                List<String> categories = List.of("Financial Crime", "Cybercrime", "Internal Fraud", "Threat Intel", "Identity Crime");
                List<String> caseTypes = List.of("Mobile Forensics", "Cloud Forensics", "Network Forensics", "Media Forensics", "Incident Response");

                List<InvestigationCase> cases = new ArrayList<>();
                for (int i = 0; i < 25; i++) {
                    CaseStatus status = switch (i % 5) {
                        case 0 -> CaseStatus.OPEN;
                        case 1 -> CaseStatus.ACTIVE;
                        case 2 -> CaseStatus.ON_HOLD;
                        case 3 -> CaseStatus.CLOSED;
                        default -> CaseStatus.ARCHIVED;
                    };
                    CasePriority priority = switch (i % 4) {
                        case 0 -> CasePriority.LOW;
                        case 1 -> CasePriority.MEDIUM;
                        case 2 -> CasePriority.HIGH;
                        default -> CasePriority.CRITICAL;
                    };
                    cases.add(caseEntity(
                            String.format("MC-2026-%03d", i + 1),
                            caseTitles.get(i),
                            "Detailed forensic investigation for " + caseTitles.get(i).toLowerCase() + " with chain-of-custody preserved.",
                            status,
                            priority,
                            investigators.get(i % investigators.size()),
                            categories.get(i % categories.size()),
                            caseTypes.get(i % caseTypes.size()),
                            "Zone-" + ((i % 6) + 1),
                            "Initial triage complete. Awaiting extended artifact correlation.",
                            i + 1
                    ));
                }

                caseRepository.saveAll(cases);
                for (int i = 0; i < cases.size(); i++) {
                    InvestigationCase item = cases.get(i);
                    Device device = new Device();
                    device.setDeviceName("Seized Device " + item.getCaseNumber().substring(item.getCaseNumber().length() - 3));
                    device.setOperatingSystem(i % 2 == 0 ? "Android" : "iOS");
                    device.setAcquisitionStatus(DeviceStatus.UNDER_ANALYSIS);
                    device.setCreatedAt(item.getCreatedAt().plusSeconds(5400));
                    device.setInvestigationCase(item);
                    deviceRepository.save(device);

                    Evidence evidence = new Evidence();
                    evidence.setArtifactType(List.of("Messages", "Images", "Documents", "Location", "Calls").get(i % 5));
                    evidence.setFileName(item.getCaseNumber() + "-artifact-" + (i + 1) + ".json");
                    evidence.setHashSha256("sha256-seeded-" + item.getCaseNumber().toLowerCase());
                    evidence.setCreatedAt(item.getCreatedAt().plusSeconds(10_800));
                    evidence.setDevice(device);
                    evidenceRepository.save(evidence);

                    Report report = new Report();
                    report.setReportNumber("RPT-" + item.getCaseNumber());
                    report.setReportTitle(item.getTitle() + " Investigation Report");
                    report.setReportType("Investigation Report");
                    report.setVersion("1.0");
                    report.setStatus(i % 4 == 0 ? "DRAFT" : "GENERATED");
                    report.setGeneratedAt(item.getUpdatedAt().plusSeconds(14_400));
                    report.setPreparedBy(item.getLeadInvestigator());
                    report.setReviewedBy(i % 4 == 0 ? "Pending Review" : "Maya Reviewer");
                    report.setApprovedBy(i % 4 == 0 ? "Pending Approval" : "System Administrator");
                    report.setGeneratedBy(item.getLeadInvestigator());
                    report.setNotesHtml("<p>Seeded forensic report generated from live case, device, and evidence records.</p>");
                    report.setAiSummary(i % 4 == 0 ? "AI Analysis Not Available Yet" : "Automated triage suggests linked messaging and location artifacts.");
                    report.setTimelineSummary("Case timeline derived from audit activity and evidence ingestion events.");
                    report.setExtractedKeywords("wallet, messages, location, device, metadata");
                    report.setNamedEntities("Aarav Sharma, MC-2026, WhatsApp, Google Drive");
                    report.setSuspiciousFindings("Potential evidence linkage with account takeover activity.");
                    report.setRiskLevel(i % 4 == 0 ? "Moderate" : "High");
                    report.setInvestigationCase(item);
                    reportRepository.save(report);
                }
            }

            if (auditLogRepository.count() < 100) {
                List<String> actors = List.of("admin", "investigator", "reviewer", "inv01", "inv02", "inv03", "inv04", "inv05", "inv06", "inv07", "inv08", "inv09", "inv10");
                List<String> actions = List.of("LOGIN", "CASE_CREATED", "DEVICE_ADDED", "EVIDENCE_UPLOADED", "REPORT_GENERATED", "CASE_UPDATED");
                List<InvestigationCase> cases = caseRepository.findAll();

                List<AuditLog> logs = new ArrayList<>();
                logs.add(new AuditLog("system", "SEED_DATA", "Module 2 enterprise sample data initialized"));
                for (int i = 0; i < 99; i++) {
                    AuditLog log = new AuditLog();
                    log.setUsername(actors.get(i % actors.size()));
                    String action = actions.get(i % actions.size());
                    log.setAction(action);
                    InvestigationCase c = cases.get(i % cases.size());
                    log.setDescription(action + " - " + c.getCaseNumber() + " - " + c.getTitle());
                    log.setTimestamp(Instant.now().minusSeconds((long) (i + 1) * 3_600));
                    log.setSuccess(true);
                    logs.add(log);
                }
                auditLogRepository.saveAll(logs);
            }
        };
    }

    private void seedUser(UserRepository userRepository, PasswordEncoder passwordEncoder, String username, String email, String fullName, String password, Set<Role> roles) {
        var existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPassword(passwordEncoder.encode(password));
            user.setRoles(roles);
            user.setEnabled(true);
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRoles(roles);
        userRepository.save(user);
    }

    private void seedInvestigators(UserRepository userRepository, PasswordEncoder passwordEncoder, Role investigatorRole) {
        List<String> names = List.of(
                "Aarav Sharma", "Maya Khan", "Jyoti Rao", "Nikhil Patel", "Aditi Mehra",
                "Rahul Menon", "Priya Singh", "Karan Desai", "Neha Verma", "Siddharth Iyer"
        );
        for (int i = 0; i < names.size(); i++) {
            String username = String.format("inv%02d", i + 1);
            String email = username + "@forensics.local";
            seedUser(userRepository, passwordEncoder, username, email, names.get(i), "Investigator@123", Set.of(investigatorRole));
        }
    }

    private InvestigationCase caseEntity(String number, String title, String description, CaseStatus status, CasePriority priority, String lead, String category, String caseType, String location, String notes, int daysAgo) {
        InvestigationCase item = new InvestigationCase();
        item.setCaseNumber(number);
        item.setTitle(title);
        item.setDescription(description);
        item.setStatus(status);
        item.setPriority(priority);
        item.setLeadInvestigator(lead);
        item.setCategory(category);
        item.setCaseType(caseType);
        item.setLocation(location);
        item.setNotes(notes);
        item.setCreatedAt(Instant.now().minusSeconds((daysAgo + 7L) * 86_400L));
        item.setUpdatedAt(Instant.now().minusSeconds(daysAgo * 86_400L));
        return item;
    }
}
