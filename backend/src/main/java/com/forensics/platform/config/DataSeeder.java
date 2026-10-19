package com.forensics.platform.config;

import com.forensics.platform.entity.*;
import com.forensics.platform.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Development seed data.
 * <ul>
 *   <li>Roles are always ensured.</li>
 *   <li>Development accounts are created only when missing (existing accounts and passwords are never changed).</li>
 *   <li>Demo cases and devices are clearly marked as synthetic. No evidence, hashes, reports, AI output or audit
 *       entries are fabricated; import the sample files in {@code datasets/samples} to create real records.</li>
 * </ul>
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seed(RoleRepository roleRepository, UserRepository userRepository, CaseRepository caseRepository,
                           DeviceRepository deviceRepository, PasswordEncoder passwordEncoder,
                           @Value("${app.security.seed-development-users:true}") boolean seedDevelopmentUsers,
                           @Value("${app.seed.demo-cases:${app.security.seed-development-users:true}}") boolean seedDemoCases) {
        return args -> {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN)));
            Role investigatorRole = roleRepository.findByName(RoleName.INVESTIGATOR).orElseGet(() -> roleRepository.save(new Role(RoleName.INVESTIGATOR)));
            Role reviewerRole = roleRepository.findByName(RoleName.REVIEWER).orElseGet(() -> roleRepository.save(new Role(RoleName.REVIEWER)));

            if (seedDevelopmentUsers) {
                createIfMissing(userRepository, passwordEncoder, "admin", "admin@forensics.local", "System Administrator", "Admin@123", adminRole);
                createIfMissing(userRepository, passwordEncoder, "investigator", "investigator@forensics.local", "Aarav Sharma", "Investigator@123", investigatorRole);
                createIfMissing(userRepository, passwordEncoder, "reviewer", "reviewer@forensics.local", "Maya Reviewer", "Reviewer@123", reviewerRole);
                List<String> names = List.of("Maya Khan", "Jyoti Rao", "Nikhil Patel", "Aditi Mehra", "Rahul Menon");
                for (int i = 0; i < names.size(); i++) {
                    String username = String.format("inv%02d", i + 1);
                    createIfMissing(userRepository, passwordEncoder, username, username + "@forensics.local", names.get(i), "Investigator@123", investigatorRole);
                }
            }

            if (seedDemoCases && caseRepository.count() == 0) {
                seedDemoCases(caseRepository, deviceRepository);
            }
        };
    }

    private void seedDemoCases(CaseRepository caseRepository, DeviceRepository deviceRepository) {
        record Demo(String title, String category, String type, CaseStatus status, CasePriority priority, String lead) {}
        List<Demo> demos = List.of(
                new Demo("Cross-border cryptocurrency wallet seizure", "Financial Crime", "Mobile Forensics", CaseStatus.ACTIVE, CasePriority.HIGH, "Aarav Sharma"),
                new Demo("Deleted chats in extortion complaint", "Cybercrime", "Mobile Forensics", CaseStatus.OPEN, CasePriority.CRITICAL, "Aarav Sharma"),
                new Demo("Freight theft coordination review", "Organised Crime", "Mobile Forensics", CaseStatus.ACTIVE, CasePriority.MEDIUM, "Aarav Sharma"),
                new Demo("Insider intellectual property theft", "Internal Fraud", "Mobile Forensics", CaseStatus.ON_HOLD, CasePriority.MEDIUM, "Maya Khan"),
                new Demo("SIM swap coordinated account takeover", "Identity Crime", "Mobile Forensics", CaseStatus.ACTIVE, CasePriority.HIGH, "Jyoti Rao"),
                new Demo("Messaging app metadata conflict review", "Cybercrime", "Mobile Forensics", CaseStatus.OPEN, CasePriority.LOW, "Nikhil Patel"),
                new Demo("Travel route geofence anomaly", "Threat Intel", "Mobile Forensics", CaseStatus.CLOSED, CasePriority.MEDIUM, "Aditi Mehra"),
                new Demo("Organised phishing kit attribution", "Cybercrime", "Mobile Forensics", CaseStatus.ARCHIVED, CasePriority.LOW, "Rahul Menon")
        );
        for (int i = 0; i < demos.size(); i++) {
            Demo demo = demos.get(i);
            InvestigationCase item = new InvestigationCase();
            item.setCaseNumber(String.format("DEMO-2026-%03d", i + 1));
            item.setTitle(demo.title());
            item.setDescription("Synthetic demo case for development. It contains no real evidence. Import the sample extraction files from datasets/samples to exercise the platform.");
            item.setStatus(demo.status());
            item.setPriority(demo.priority());
            item.setLeadInvestigator(demo.lead());
            item.setCategory(demo.category());
            item.setCaseType(demo.type());
            item.setLocation("Demo location");
            item.setNotes("Demo data.");
            caseRepository.save(item);

            Device device = new Device();
            device.setDeviceName("Demo handset " + (i + 1));
            device.setManufacturer("Unknown");
            device.setModel("Unknown");
            device.setOperatingSystem(i % 2 == 0 ? "Android" : "iOS");
            device.setAcquisitionStatus(DeviceStatus.WAITING_FOR_EXTRACTION);
            device.setNotes("Synthetic demo device. No evidence attached.");
            device.setCreatedBy("system");
            device.setUpdatedBy("system");
            device.setInvestigationCase(item);
            deviceRepository.save(device);
        }
        log.info("Seeded {} synthetic demo cases (no evidence)", demos.size());
    }

    private void createIfMissing(UserRepository userRepository, PasswordEncoder passwordEncoder, String username, String email,
                                 String fullName, String password, Role role) {
        if (userRepository.findByUsername(username).isPresent() || userRepository.findByEmail(email).isPresent()) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRoles(new java.util.HashSet<>(Set.of(role)));
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
    }
}
