package com.forensics.platform.service;

import com.forensics.platform.dto.RelationshipGraphDto;
import com.forensics.platform.dto.RelationshipGraphDto.Entity;
import com.forensics.platform.dto.RelationshipGraphDto.Relationship;
import com.forensics.platform.dto.RelationshipGraphDto.Rule;
import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.entity.NormalizedEvidenceCategory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic, explainable correlation of evidence records. Pure logic: no I/O, no randomness,
 * so the same input always yields the same relationships (reproducible for reports).
 *
 * <p>Identifier handling: phone-like values are compared on their last 10 digits so that
 * {@code +91 98450 11223} and {@code 9845011223} match. When differently formatted values are
 * merged this way the entity carries a note, and record-to-record links that depend only on the
 * merge are marked {@code INFERRED}.</p>
 */
public final class CorrelationEngine {

    public static final String VERIFIED = "VERIFIED_RECORD";
    public static final String INFERRED = "INFERRED";

    public static final String DISCLAIMER = "Correlations show that records share identifiers, locations, content or timing. "
            + "They do not establish who used a device or identifier, and they are not evidence of intent or wrongdoing. "
            + "Verify each relationship against the cited source records.";

    public static final List<Rule> RULES = List.of(
            new Rule("COMMUNICATION_RECORD", VERIFIED, "A communication record (SMS, call, chat, email) lists both identifiers as sender and recipient."),
            new Rule("ADDRESS_BOOK_ENTRY", VERIFIED, "A contact record stores the name together with the identifier. Shows how a device user labelled the identifier; not proof of identity."),
            new Rule("RECORD_LABEL", VERIFIED, "A non-contact record labels the identifier with a contact name."),
            new Rule("OBSERVED_ON_DEVICE", VERIFIED, "The identifier, name or location appears in records extracted from the device."),
            new Rule("SHARED_IDENTIFIER", INFERRED, "The same identifier (compared on its last 10 digits for phone numbers) appears in records from two devices."),
            new Rule("CROSS_DEVICE_CORROBORATION", INFERRED, "Communication records on two devices have the same participants and content (or call duration) within 2 minutes, so they likely describe the same event."),
            new Rule("TEMPORAL_PROXIMITY", INFERRED, "On one device, a communication and a location record were recorded within the time window. Proximity in time does not show where the communication happened.")
    );

    private static final Duration CORROBORATION_WINDOW = Duration.ofMinutes(2);
    private static final int MAX_EVIDENCE_IDS_PER_EDGE = 200;
    private static final int MAX_EVENTS = 500;
    private static final DateTimeFormatter EVENT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);

    private CorrelationEngine() {
    }

    // ---------------------------------------------------------------- identifiers

    public record Identifier(String key, String type, String raw) {}

    /**
     * Normalized key for a phone-like value (last 10 digits), or null when the value is not phone-like.
     */
    public static String phoneKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || !value.matches("[+0-9()\\-.\\s]+")) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7 || digits.length() > 15) {
            return null;
        }
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    public static Identifier communicationIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phone = phoneKey(raw);
        if (phone != null) {
            return new Identifier("phone:" + phone, "PHONE", raw.trim());
        }
        String trimmed = raw.trim();
        return new Identifier("account:" + trimmed.toLowerCase(Locale.ROOT), "ACCOUNT", trimmed);
    }

    public static Identifier person(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim().replaceAll("\\s+", " ");
        return new Identifier("person:" + trimmed.toLowerCase(Locale.ROOT), "PERSON", trimmed);
    }

    public static Identifier location(Evidence e) {
        if (e.getLocation() != null && !e.getLocation().isBlank()) {
            String trimmed = e.getLocation().trim().replaceAll("\\s+", " ");
            return new Identifier("location:" + trimmed.toLowerCase(Locale.ROOT), "LOCATION", trimmed);
        }
        if (e.getLatitude() != null && e.getLongitude() != null) {
            String cell = String.format(Locale.ROOT, "%.3f, %.3f", e.getLatitude(), e.getLongitude());
            return new Identifier("geo:" + cell, "LOCATION", cell);
        }
        return null;
    }

    /** Sender, recipient and phone-number identifiers of a record, de-duplicated by key. */
    public static List<Identifier> participants(Evidence e) {
        Map<String, Identifier> byKey = new LinkedHashMap<>();
        for (String value : new String[]{e.getSource(), e.getTarget(), e.getPhoneNumber()}) {
            Identifier id = communicationIdentifier(value);
            if (id != null) {
                byKey.putIfAbsent(id.key(), id);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** Every identifier-like entity a record references. */
    public static List<Identifier> allIdentifiers(Evidence e) {
        List<Identifier> ids = new ArrayList<>(participants(e));
        Identifier person = person(e.getContactName());
        if (person != null) {
            ids.add(person);
        }
        Identifier location = location(e);
        if (location != null) {
            ids.add(location);
        }
        return ids;
    }

    static String corroborationKey(Evidence e) {
        if (EvidenceCategories.resolve(e.getArtifactType()) != NormalizedEvidenceCategory.COMMUNICATION || e.getEventTime() == null) {
            return null;
        }
        List<String> keys = participants(e).stream().map(Identifier::key).sorted().toList();
        if (keys.size() < 2) {
            return null;
        }
        String body;
        if (e.getContent() != null && !e.getContent().isBlank()) {
            body = "text:" + e.getContent().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        } else {
            String digits = e.getTitle() == null ? "" : e.getTitle().replaceAll("\\D", "");
            if (digits.isEmpty()) {
                return null;
            }
            body = "duration:" + digits;
        }
        return String.join("|", keys) + "|" + body;
    }

    // ---------------------------------------------------------------- graph

    public record Options(Long caseId, Long deviceId, boolean includeEvents, Duration proximityWindow, int maxEntities) {}

    public static RelationshipGraphDto build(List<Evidence> evidence, Options options) {
        Builder builder = new Builder();
        Map<Long, Device> devices = new LinkedHashMap<>();
        for (Evidence e : evidence) {
            if (e.getDevice() != null) {
                devices.putIfAbsent(e.getDevice().getId(), e.getDevice());
            }
        }
        for (Device device : devices.values()) {
            builder.entity("device:" + device.getId(), "DEVICE", deviceLabel(device), null, null, device.getId());
        }

        // identifier key -> device id -> evidence ids (for SHARED_IDENTIFIER)
        Map<String, Map<Long, Set<Long>>> identifierDevices = new HashMap<>();

        for (Evidence e : evidence) {
            Long deviceId = e.getDevice() == null ? null : e.getDevice().getId();
            Instant time = e.getEventTime();
            String deviceKey = deviceId == null ? null : "device:" + deviceId;
            NormalizedEvidenceCategory category = EvidenceCategories.resolve(e.getArtifactType());
            String type = EvidenceCategories.normalizeType(e.getArtifactType());

            List<Identifier> participants = participants(e);
            Identifier person = person(e.getContactName());
            Identifier place = location(e);

            if (deviceKey != null) {
                builder.entity(deviceKey, "DEVICE", null, null, e, deviceId);
            }
            for (Identifier id : participants) {
                builder.entity(id.key(), id.type(), id.raw(), id.raw(), e, deviceId);
            }
            if (person != null) {
                builder.entity(person.key(), person.type(), person.raw(), person.raw(), e, deviceId);
            }
            if (place != null) {
                builder.entity(place.key(), place.type(), place.raw(), place.raw(), e, deviceId);
            }

            // R1: communication between sender and recipient
            Identifier sender = communicationIdentifier(e.getSource());
            Identifier recipient = communicationIdentifier(e.getTarget());
            if (category == NormalizedEvidenceCategory.COMMUNICATION && sender != null && recipient != null && !sender.key().equals(recipient.key())) {
                Builder.Edge edge = builder.edge("COMMUNICATED_WITH", sender.key(), recipient.key(), VERIFIED, "COMMUNICATION_RECORD", e);
                edge.types.add(type);
                edge.direction.merge(sender.key() + ">" + recipient.key(), 1, Integer::sum);
            }

            // R2 / R2b: name attached to identifier
            if (person != null) {
                for (Identifier id : participants) {
                    if (type.equals("CONTACT")) {
                        builder.edge("CONTACT_ENTRY", person.key(), id.key(), VERIFIED, "ADDRESS_BOOK_ENTRY", e);
                    } else if (e.getPhoneNumber() != null && id.key().equals(keyOf(e.getPhoneNumber()))) {
                        builder.edge("LABELLED_AS", person.key(), id.key(), VERIFIED, "RECORD_LABEL", e);
                    }
                }
            }

            // R3 / R4: what each device's records reference
            if (deviceKey != null) {
                for (Identifier id : participants) {
                    builder.edge("APPEARS_ON_DEVICE", deviceKey, id.key(), VERIFIED, "OBSERVED_ON_DEVICE", e);
                    identifierDevices.computeIfAbsent(id.key(), k -> new TreeMap<>()).computeIfAbsent(deviceId, k -> new TreeSet<>()).add(e.getId());
                }
                if (person != null) {
                    builder.edge("APPEARS_ON_DEVICE", deviceKey, person.key(), VERIFIED, "OBSERVED_ON_DEVICE", e);
                    identifierDevices.computeIfAbsent(person.key(), k -> new TreeMap<>()).computeIfAbsent(deviceId, k -> new TreeSet<>()).add(e.getId());
                }
                if (place != null) {
                    builder.edge("RECORDED_AT", deviceKey, place.key(), VERIFIED, "OBSERVED_ON_DEVICE", e);
                }
            }

            if (options.includeEvents() && builder.eventCount < MAX_EVENTS) {
                builder.eventCount++;
                String eventKey = "event:" + e.getId();
                String label = type + (time == null ? " (no timestamp)" : " " + EVENT_FORMAT.format(time));
                builder.entity(eventKey, "EVENT", label, null, e, deviceId);
                for (Identifier id : participants) {
                    builder.edge("PARTICIPANT", eventKey, id.key(), VERIFIED, "COMMUNICATION_RECORD", e);
                }
                if (place != null) {
                    builder.edge("AT_LOCATION", eventKey, place.key(), VERIFIED, "OBSERVED_ON_DEVICE", e);
                }
                if (deviceKey != null) {
                    builder.edge("EXTRACTED_FROM", eventKey, deviceKey, VERIFIED, "OBSERVED_ON_DEVICE", e);
                }
            }
        }

        // R5: identifiers shared between devices
        Map<String, List<String>> sharedByPair = new TreeMap<>();
        for (var entry : identifierDevices.entrySet()) {
            List<Long> deviceIds = new ArrayList<>(entry.getValue().keySet());
            for (int i = 0; i < deviceIds.size(); i++) {
                for (int j = i + 1; j < deviceIds.size(); j++) {
                    Builder.Edge edge = builder.edge("SHARED_IDENTIFIER", "device:" + deviceIds.get(i), "device:" + deviceIds.get(j), INFERRED, "SHARED_IDENTIFIER", null);
                    edge.addEvidence(entry.getValue().get(deviceIds.get(i)));
                    edge.addEvidence(entry.getValue().get(deviceIds.get(j)));
                    sharedByPair.computeIfAbsent(edge.key, k -> new ArrayList<>()).add(builder.labelOf(entry.getKey()));
                }
            }
        }
        sharedByPair.forEach((key, labels) -> builder.edges.get(key).detail = labels.stream().sorted().toList());

        // R6: the same communication seen on two devices
        Map<String, List<Evidence>> byCorroborationKey = new HashMap<>();
        for (Evidence e : evidence) {
            String key = corroborationKey(e);
            if (key != null && e.getDevice() != null) {
                byCorroborationKey.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
        }
        for (List<Evidence> group : byCorroborationKey.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    Evidence a = group.get(i);
                    Evidence b = group.get(j);
                    if (Objects.equals(a.getDevice().getId(), b.getDevice().getId())) {
                        continue;
                    }
                    if (Duration.between(a.getEventTime(), b.getEventTime()).abs().compareTo(CORROBORATION_WINDOW) <= 0) {
                        Builder.Edge edge = builder.edge("CORROBORATED_BY", "device:" + a.getDevice().getId(), "device:" + b.getDevice().getId(), INFERRED, "CROSS_DEVICE_CORROBORATION", a);
                        edge.addEvidence(b);
                    }
                }
            }
        }

        // R7: communications near in time to location records on the same device
        if (options.proximityWindow() != null && !options.proximityWindow().isZero()) {
            Map<Long, List<Evidence>> byDevice = evidence.stream()
                    .filter(e -> e.getDevice() != null && e.getEventTime() != null)
                    .collect(Collectors.groupingBy(e -> e.getDevice().getId(), TreeMap::new, Collectors.toList()));
            for (List<Evidence> deviceRecords : byDevice.values()) {
                List<Evidence> locations = deviceRecords.stream().filter(e -> location(e) != null
                        && EvidenceCategories.resolve(e.getArtifactType()) != NormalizedEvidenceCategory.COMMUNICATION).toList();
                List<Evidence> communications = deviceRecords.stream()
                        .filter(e -> EvidenceCategories.resolve(e.getArtifactType()) == NormalizedEvidenceCategory.COMMUNICATION).toList();
                for (Evidence loc : locations) {
                    Identifier place = location(loc);
                    for (Evidence comm : communications) {
                        if (Duration.between(loc.getEventTime(), comm.getEventTime()).abs().compareTo(options.proximityWindow()) <= 0) {
                            for (Identifier id : participants(comm)) {
                                Builder.Edge edge = builder.edge("NEAR_IN_TIME", place.key(), id.key(), INFERRED, "TEMPORAL_PROXIMITY", loc);
                                edge.addEvidence(comm);
                                long minutes = Duration.between(loc.getEventTime(), comm.getEventTime()).abs().toMinutes();
                                edge.minGapMinutes = edge.minGapMinutes == null ? minutes : Math.min(edge.minGapMinutes, minutes);
                            }
                        }
                    }
                }
            }
        }

        return builder.result(options, evidence.size());
    }

    private static String keyOf(String raw) {
        Identifier id = communicationIdentifier(raw);
        return id == null ? null : id.key();
    }

    public static String deviceLabel(Device device) {
        String name = device.getDeviceName();
        if (name == null || name.isBlank()) {
            name = "Device #" + device.getId();
        }
        return device.getImei() == null ? name : name + " (IMEI " + device.getImei() + ")";
    }

    // ---------------------------------------------------------------- accumulation

    private static final class Builder {
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final Map<String, Edge> edges = new LinkedHashMap<>();
        private int eventCount;

        private static final class Node {
            String id;
            String type;
            String label;
            final Set<String> variants = new TreeSet<>();
            final Set<Long> evidence = new TreeSet<>();
            final Set<Long> devices = new TreeSet<>();
            Instant first;
            Instant last;
        }

        private static final class Edge {
            String key;
            String type;
            String source;
            String target;
            String basis;
            String rule;
            final Set<Long> evidence = new TreeSet<>();
            final Set<String> types = new TreeSet<>();
            final Map<String, Integer> direction = new TreeMap<>();
            List<String> detail = List.of();
            Long minGapMinutes;
            Instant first;
            Instant last;

            void addEvidence(Evidence e) {
                if (e == null) {
                    return;
                }
                evidence.add(e.getId());
                Instant t = e.getEventTime();
                if (t != null) {
                    first = first == null || t.isBefore(first) ? t : first;
                    last = last == null || t.isAfter(last) ? t : last;
                }
            }

            void addEvidence(Collection<Long> ids) {
                evidence.addAll(ids);
            }
        }

        void entity(String key, String type, String label, String variant, Evidence e, Long deviceId) {
            Node node = nodes.computeIfAbsent(key, k -> {
                Node created = new Node();
                created.id = k;
                created.type = type;
                created.label = label;
                return created;
            });
            if (variant != null) {
                node.variants.add(variant);
            }
            if (deviceId != null) {
                node.devices.add(deviceId);
            }
            if (e != null) {
                node.evidence.add(e.getId());
                Instant t = e.getEventTime();
                if (t != null) {
                    node.first = node.first == null || t.isBefore(node.first) ? t : node.first;
                    node.last = node.last == null || t.isAfter(node.last) ? t : node.last;
                }
            }
        }

        Edge edge(String type, String a, String b, String basis, String rule, Evidence e) {
            boolean ordered = a.compareTo(b) <= 0;
            String source = ordered ? a : b;
            String target = ordered ? b : a;
            String key = type + "|" + source + "|" + target;
            Edge edge = edges.computeIfAbsent(key, k -> {
                Edge created = new Edge();
                created.key = k;
                created.type = type;
                created.source = source;
                created.target = target;
                created.basis = basis;
                created.rule = rule;
                return created;
            });
            edge.addEvidence(e);
            return edge;
        }

        String labelOf(String key) {
            Node node = nodes.get(key);
            return node == null ? key : node.label;
        }

        RelationshipGraphDto result(Options options, int evidenceConsidered) {
            Set<String> kept = new HashSet<>();
            List<Node> ordered = new ArrayList<>(nodes.values());
            ordered.sort(Comparator.comparing((Node n) -> !"DEVICE".equals(n.type))
                    .thenComparing(n -> -n.evidence.size())
                    .thenComparing(n -> n.id));
            boolean truncated = ordered.size() > options.maxEntities();
            for (int i = 0; i < Math.min(ordered.size(), options.maxEntities()); i++) {
                kept.add(ordered.get(i).id);
            }

            List<Entity> entities = nodes.values().stream()
                    .filter(n -> kept.contains(n.id))
                    .map(n -> new Entity(n.id, n.type, n.label, List.copyOf(n.variants), n.evidence.size(),
                            List.copyOf(n.devices), n.first, n.last, entityNote(n)))
                    .toList();

            Map<String, String> labels = nodes.values().stream().collect(Collectors.toMap(n -> n.id, n -> n.label));
            List<Relationship> relationships = edges.values().stream()
                    .filter(e -> kept.contains(e.source) && kept.contains(e.target))
                    .map(e -> toRelationship(e, labels::get))
                    .toList();

            return new RelationshipGraphDto(options.caseId(), options.deviceId(), Instant.now(), evidenceConsidered,
                    entities, relationships, RULES, DISCLAIMER, truncated);
        }

        private static String entityNote(Node n) {
            if ("PHONE".equals(n.type)) {
                Set<String> digitForms = n.variants.stream().map(v -> v.replaceAll("\\D", "")).collect(Collectors.toCollection(TreeSet::new));
                if (digitForms.size() > 1) {
                    return "Merged " + n.variants.size() + " differently formatted values by comparing the last 10 digits ("
                            + String.join(", ", n.variants) + "). This merge is an inference.";
                }
            }
            if ("LOCATION".equals(n.type) && n.id.startsWith("geo:")) {
                return "Coordinates rounded to 3 decimal places (about 110 m).";
            }
            return null;
        }

        private static Relationship toRelationship(Edge e, Function<String, String> label) {
            List<Long> ids = e.evidence.stream().limit(MAX_EVIDENCE_IDS_PER_EDGE).toList();
            int count = e.evidence.size();
            String a = label.apply(e.source);
            String b = label.apply(e.target);
            String explanation = switch (e.rule) {
                case "COMMUNICATION_RECORD" -> e.type.equals("PARTICIPANT")
                        ? "The record lists " + b + " as a participant."
                        : count + " " + String.join("/", e.types) + " record(s) list " + a + " and " + b + " as sender and recipient"
                          + directionText(e, label) + ".";
                case "ADDRESS_BOOK_ENTRY" -> count + " contact record(s) store the name '" + a + "' with " + b
                        + ". This shows how a device user labelled the number, not who uses it.";
                case "RECORD_LABEL" -> count + " record(s) label " + b + " with the name '" + a + "'.";
                case "OBSERVED_ON_DEVICE" -> switch (e.type) {
                    case "RECORDED_AT" -> count + " record(s) extracted from " + a + " reference the location " + b + ".";
                    case "AT_LOCATION" -> "The record references the location " + b + ".";
                    case "EXTRACTED_FROM" -> "The record was extracted from " + b + ".";
                    default -> b + " appears in " + count + " record(s) extracted from " + a + ".";
                };
                case "SHARED_IDENTIFIER" -> "Records from both devices reference " + String.join(", ", e.detail.stream().limit(5).toList())
                        + (e.detail.size() > 5 ? " and " + (e.detail.size() - 5) + " more" : "")
                        + ". Shared identifiers connect the devices' data, not necessarily their users.";
                case "CROSS_DEVICE_CORROBORATION" -> (count / 2 == 0 ? 1 : count / 2) + " communication(s) appear on both devices with the same participants and content within 2 minutes; each record corroborates the other.";
                case "TEMPORAL_PROXIMITY" -> "Communication involving " + b + " was recorded within " + (e.minGapMinutes == null ? "?" : e.minGapMinutes)
                        + " minute(s) of a location record at " + a + " on the same device. This is proximity in time only.";
                default -> count + " supporting record(s).";
            };
            return new Relationship(e.key, e.source, e.target, e.type, e.basis, e.rule, explanation, count, ids,
                    count > MAX_EVIDENCE_IDS_PER_EDGE, e.first, e.last);
        }

        private static String directionText(Edge e, Function<String, String> label) {
            if (e.direction.isEmpty()) {
                return "";
            }
            return " (" + e.direction.entrySet().stream()
                    .map(entry -> {
                        String[] parts = entry.getKey().split(">");
                        return label.apply(parts[0]) + " → " + label.apply(parts[1]) + ": " + entry.getValue();
                    })
                    .collect(Collectors.joining("; ")) + ")";
        }
    }
}
