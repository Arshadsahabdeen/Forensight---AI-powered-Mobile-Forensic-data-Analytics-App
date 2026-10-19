package com.forensics.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forensics.platform.dto.AIAnalysisResponse;
import com.forensics.platform.dto.AIAnalysisResponse.Citation;
import com.forensics.platform.dto.AIAnalysisResponse.EntityMention;
import com.forensics.platform.dto.AIAnalysisResponse.Statement;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Evidence-grounded assistant. The backend selects case-scoped records, sends only those records to the
 * AI service, and validates the response: statements, citations and entities that reference records not
 * supplied (or entities not present in the cited records) are removed and counted. When the AI service is
 * unreachable a clearly labelled keyword fallback is returned instead of pretending analysis succeeded.
 *
 * <p>Evidence content is never logged.</p>
 */
@Service
public class AIAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AIAnalysisService.class);

    public static final String DISCLAIMER = "Generated from the cited evidence records only. Statements marked INFERENCE and any "
            + "AI-generated answer text are interpretations that an investigator must verify against the source records. "
            + "Nothing here establishes identity, intent or wrongdoing.";

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "at", "for", "with", "by", "from", "is", "are", "was", "were",
            "be", "been", "it", "this", "that", "these", "those", "what", "which", "who", "whom", "when", "where", "why", "how",
            "did", "do", "does", "any", "all", "show", "find", "list", "me", "about", "there", "their", "they", "them", "has",
            "have", "had", "can", "could", "should", "would", "between", "messages", "records", "evidence", "please");

    private static final Pattern BRACKET_CITATION = Pattern.compile("\\[(\\d{1,12})]");

    private final EvidenceRepository evidenceRepository;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String serviceUrl;
    private final Duration timeout;
    private final int defaultMaxRecords;

    public AIAnalysisService(EvidenceRepository evidenceRepository, CaseAccessService caseAccessService, AuditService auditService,
                             ObjectMapper objectMapper,
                             @Value("${app.ai.service-url:http://localhost:8000}") String serviceUrl,
                             @Value("${app.ai.timeout-seconds:60}") long timeoutSeconds,
                             @Value("${app.ai.max-records:150}") int defaultMaxRecords) {
        this.evidenceRepository = evidenceRepository;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.serviceUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.defaultMaxRecords = defaultMaxRecords;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    }

    // Not transactional: the AI call can be slow and must not hold a database connection.
    // findAllForCase fetch-joins everything the payload needs.
    public AIAnalysisResponse assist(Long caseId, AIAnalysisResponse.Request request, Authentication authentication) {
        caseAccessService.requireViewable(caseId, authentication);
        String question = request.question() == null ? "" : request.question().trim();
        if ((request.task() == AIAnalysisResponse.Task.SEARCH || request.task() == AIAnalysisResponse.Task.ASK) && question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A question or search text is required for this task");
        }
        int maxRecords = request.maxRecords() == null ? defaultMaxRecords : request.maxRecords();

        List<Evidence> all = evidenceRepository.findAllForCase(caseId).stream()
                .filter(e -> request.deviceId() == null || Objects.equals(e.getDevice().getId(), request.deviceId()))
                .toList();
        List<Evidence> selected = select(all, question, request.task(), maxRecords);

        AIAnalysisResponse response;
        if (selected.isEmpty()) {
            response = new AIAnalysisResponse(request.task().name(), "none", null, "NO_EVIDENCE",
                    all.isEmpty() ? "This case has no evidence records yet. Import an extraction file first."
                            : "No evidence records matched the question, so no answer was generated.",
                    false, List.of(), List.of(), List.of(),
                    List.of("The assistant only answers from matching evidence records; it does not guess."),
                    DISCLAIMER, 0, all.size(), 0, Instant.now());
        } else {
            response = callAiService(request.task(), question, selected, all.size());
        }

        auditService.record(new AuditService.Entry(null, authentication.getName(), "AI_ASSISTANT_QUERY",
                "Assistant task " + request.task() + " using engine '" + response.engine() + "' over " + response.evidenceConsidered()
                        + " record(s); status " + response.status() + "; question length " + question.length() + " chars",
                true, caseId, "CASE", String.valueOf(caseId), null));
        return response;
    }

    public AIAnalysisResponse.Status status() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(serviceUrl + "/v1/config"))
                    .timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new AIAnalysisResponse.Status(false, "unavailable", null, null, false, false,
                        "AI service responded with HTTP " + response.statusCode() + ".", serviceUrl);
            }
            JsonNode node = objectMapper.readTree(response.body());
            return new AIAnalysisResponse.Status(true, text(node, "engine"), text(node, "provider"), text(node, "model"),
                    node.path("llmConfigured").asBoolean(false), node.path("externalProcessingAllowed").asBoolean(false),
                    text(node, "message"), serviceUrl);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new AIAnalysisResponse.Status(false, "unavailable", null, null, false, false,
                    "AI service is not reachable at the configured URL. The assistant will use the backend keyword fallback.", serviceUrl);
        }
    }

    // ------------------------------------------------------------------ retrieval

    /**
     * Chooses the records sent to the AI service. With a question, records are ranked by term matches
     * (and normalized phone matches); without one, the most recent records are used.
     */
    static List<Evidence> select(List<Evidence> all, String question, AIAnalysisResponse.Task task, int maxRecords) {
        List<String> terms = terms(question);
        List<String> phoneKeys = Arrays.stream(question.split("[\\s,;]+"))
                .map(CorrelationEngine::phoneKey).filter(Objects::nonNull).distinct().toList();
        boolean ranked = !terms.isEmpty() || !phoneKeys.isEmpty();

        if (!ranked) {
            if (task == AIAnalysisResponse.Task.SEARCH || task == AIAnalysisResponse.Task.ASK) {
                return List.of();
            }
            return all.stream()
                    .sorted(Comparator.comparing((Evidence e) -> e.getEventTime() == null ? Instant.MIN : e.getEventTime()).reversed()
                            .thenComparing(Evidence::getId))
                    .limit(maxRecords)
                    .sorted(Comparator.comparing(Evidence::getId))
                    .toList();
        }

        record Scored(Evidence evidence, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (Evidence e : all) {
            int score = score(e, terms, phoneKeys);
            if (score > 0) {
                scored.add(new Scored(e, score));
            }
        }
        List<Evidence> matches = scored.stream()
                .sorted(Comparator.comparingInt((Scored s) -> -s.score()).thenComparing(s -> s.evidence().getId()))
                .limit(maxRecords)
                .map(Scored::evidence)
                .toList();
        if (matches.isEmpty() && (task == AIAnalysisResponse.Task.SUMMARIZE || task == AIAnalysisResponse.Task.ENTITIES)) {
            return select(all, "", task, maxRecords);
        }
        return matches;
    }

    static List<String> terms(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}@.+_-]+"))
                .map(token -> token.replaceAll("^[.+_-]+|[.+_-]+$", ""))
                .filter(token -> token.length() >= 2 && !STOPWORDS.contains(token))
                .distinct()
                .toList();
    }

    private static int score(Evidence e, List<String> terms, List<String> phoneKeys) {
        int score = 0;
        String primary = join(e.getContent(), e.getTitle()).toLowerCase(Locale.ROOT);
        String secondary = join(e.getSource(), e.getTarget(), e.getPhoneNumber(), e.getContactName(), e.getLocation(), e.getArtifactType())
                .toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (primary.contains(term)) {
                score += 3;
            }
            if (secondary.contains(term)) {
                score += 2;
            }
        }
        for (String key : phoneKeys) {
            for (String value : new String[]{e.getSource(), e.getTarget(), e.getPhoneNumber()}) {
                if (key.equals(CorrelationEngine.phoneKey(value))) {
                    score += 5;
                }
            }
        }
        return score;
    }

    // ------------------------------------------------------------------ AI service call and grounding

    private AIAnalysisResponse callAiService(AIAnalysisResponse.Task task, String question, List<Evidence> selected, int available) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task", task.name().toLowerCase(Locale.ROOT));
        body.put("question", question);
        body.put("records", selected.stream().map(AIAnalysisService::toPayload).toList());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(serviceUrl + "/v1/assist"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI service returned HTTP {}", response.statusCode());
                return fallback(task, question, selected, available, "AI service returned HTTP " + response.statusCode() + ".");
            }
            return ground(task, objectMapper.readTree(response.body()), selected, available);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("AI service unavailable: {}", ex.getClass().getSimpleName());
            return fallback(task, question, selected, available, "AI service unavailable (" + ex.getClass().getSimpleName() + ").");
        }
    }

    /**
     * Validates an AI service response against the records that were actually sent.
     */
    static AIAnalysisResponse ground(AIAnalysisResponse.Task task, JsonNode node, List<Evidence> sent, int available) {
        Map<Long, Evidence> allowed = sent.stream().collect(Collectors.toMap(Evidence::getId, e -> e, (a, b) -> a, LinkedHashMap::new));
        int dropped = 0;
        List<String> limitations = new ArrayList<>();
        node.path("limitations").forEach(item -> {
            if (item.isTextual()) {
                limitations.add(truncate(item.asText(), 500));
            }
        });

        List<Statement> statements = new ArrayList<>();
        for (JsonNode item : node.path("statements")) {
            List<Long> ids = validIds(item.path("evidenceIds"), allowed.keySet());
            String text = truncate(item.path("text").asText(""), 2000);
            if (ids.isEmpty() || text.isBlank()) {
                dropped++;
                continue;
            }
            String kind = item.path("kind").asText("INFERENCE").toUpperCase(Locale.ROOT);
            if (!Set.of("RECORD_FACT", "AGGREGATE", "INFERENCE").contains(kind)) {
                kind = "INFERENCE";
            }
            statements.add(new Statement(text, kind, ids));
        }

        List<Citation> citations = new ArrayList<>();
        Set<Long> cited = new HashSet<>();
        for (JsonNode item : node.path("citations")) {
            long id = item.path("evidenceId").asLong(-1);
            Evidence e = allowed.get(id);
            if (e == null) {
                dropped++;
                continue;
            }
            if (cited.add(id)) {
                // The snippet always comes from the stored record, never from the AI response.
                citations.add(citation(e, truncate(item.path("reason").asText(""), 500)));
            }
        }

        List<EntityMention> entities = new ArrayList<>();
        for (JsonNode item : node.path("entities")) {
            String value = truncate(item.path("value").asText(""), 255).trim();
            List<Long> ids = validIds(item.path("evidenceIds"), allowed.keySet()).stream()
                    .filter(id -> appearsIn(value, allowed.get(id)))
                    .toList();
            if (value.isEmpty() || ids.isEmpty()) {
                dropped++;
                continue;
            }
            entities.add(new EntityMention(truncate(item.path("type").asText("OTHER").toUpperCase(Locale.ROOT), 40), value, ids));
        }

        String engine = text(node, "engine") == null ? "unknown" : text(node, "engine");
        String answer = truncate(node.path("answer").asText(""), 8000);
        Matcher matcher = BRACKET_CITATION.matcher(answer);
        Set<Long> unknownRefs = new TreeSet<>();
        while (matcher.find()) {
            long id = Long.parseLong(matcher.group(1));
            if (!allowed.containsKey(id)) {
                unknownRefs.add(id);
            }
        }
        if (!unknownRefs.isEmpty()) {
            limitations.add("The answer text referenced record IDs that were not supplied " + unknownRefs + "; treat those parts as unsupported.");
            dropped += unknownRefs.size();
        }
        if (dropped > 0) {
            limitations.add(dropped + " item(s) were removed because they cited records that were not supplied or values not found in the cited records.");
        }
        if (sent.size() < available) {
            limitations.add("Only " + sent.size() + " of " + available + " case record(s) were provided to the assistant.");
        }
        boolean aiText = "llm".equalsIgnoreCase(engine);
        return new AIAnalysisResponse(task.name(), engine, text(node, "model"), "COMPLETED", answer, aiText, statements, citations,
                entities, limitations, DISCLAIMER, sent.size(), available, dropped, Instant.now());
    }

    static AIAnalysisResponse fallback(AIAnalysisResponse.Task task, String question, List<Evidence> selected, int available, String reason) {
        List<Citation> citations = selected.stream().limit(25).map(e -> citation(e,
                question.isBlank() ? "Recent record in scope." : "Contains terms from the query.")).toList();
        List<Statement> statements = selected.stream().limit(25).map(e -> new Statement(
                describe(e), "RECORD_FACT", List.of(e.getId()))).toList();

        Map<String, EntityMention> entities = new LinkedHashMap<>();
        for (Evidence e : selected) {
            for (CorrelationEngine.Identifier id : CorrelationEngine.allIdentifiers(e)) {
                entities.compute(id.key(), (key, existing) -> {
                    List<Long> ids = new ArrayList<>(existing == null ? List.of() : existing.evidenceIds());
                    if (!ids.contains(e.getId())) {
                        ids.add(e.getId());
                    }
                    return new EntityMention(id.type(), existing == null ? id.raw() : existing.value(), ids);
                });
            }
        }
        List<String> limitations = new ArrayList<>();
        limitations.add(reason + " Results are keyword matches and identifiers extracted by the backend; no AI interpretation was performed.");
        if (selected.size() < available) {
            limitations.add("Only " + selected.size() + " of " + available + " case record(s) were considered.");
        }
        String answer = "AI analysis is unavailable. " + selected.size() + " record(s) "
                + (question.isBlank() ? "in scope are" : "matched the query and are") + " listed below for manual review.";
        return new AIAnalysisResponse(task.name(), "backend-keyword-fallback", null, "FALLBACK", answer, false, statements, citations,
                new ArrayList<>(entities.values()), limitations, DISCLAIMER, selected.size(), available, 0, Instant.now());
    }

    private static Map<String, Object> toPayload(Evidence e) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", e.getId());
        record.put("type", EvidenceCategories.normalizeType(e.getArtifactType()));
        record.put("category", EvidenceCategories.resolve(e.getArtifactType()).name());
        record.put("time", e.getEventTime() == null ? null : e.getEventTime().toString());
        record.put("timestampStatus", e.getTimestampStatus() == null ? null : e.getTimestampStatus().name());
        record.put("title", e.getTitle());
        record.put("content", truncate(e.getContent(), 2000));
        record.put("source", e.getSource());
        record.put("target", e.getTarget());
        record.put("phoneNumber", e.getPhoneNumber());
        record.put("contactName", e.getContactName());
        record.put("location", e.getLocation());
        record.put("device", e.getDevice() == null ? null : e.getDevice().getDeviceName());
        return record;
    }

    private static Citation citation(Evidence e, String reason) {
        String snippet = e.getContent() != null ? e.getContent() : e.getTitle();
        return new Citation(e.getId(), reason, truncate(snippet, 240), e.getArtifactType(), e.getEventTime(),
                e.getDevice() == null ? null : e.getDevice().getDeviceName());
    }

    private static String describe(Evidence e) {
        StringBuilder text = new StringBuilder(EvidenceCategories.normalizeType(e.getArtifactType()));
        text.append(e.getEventTime() == null ? " (no timestamp)" : " at " + e.getEventTime());
        if (e.getSource() != null || e.getTarget() != null) {
            text.append(" from ").append(e.getSource() == null ? "?" : e.getSource()).append(" to ").append(e.getTarget() == null ? "?" : e.getTarget());
        }
        String body = e.getContent() != null ? e.getContent() : e.getTitle();
        if (body != null) {
            text.append(": \"").append(truncate(body, 160)).append('"');
        }
        return text.toString();
    }

    private static boolean appearsIn(String value, Evidence e) {
        if (e == null || value.isBlank()) {
            return false;
        }
        String haystack = join(e.getTitle(), e.getContent(), e.getSource(), e.getTarget(), e.getPhoneNumber(), e.getContactName(),
                e.getLocation()).toLowerCase(Locale.ROOT);
        if (haystack.contains(value.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String key = CorrelationEngine.phoneKey(value);
        if (key != null) {
            for (String field : new String[]{e.getSource(), e.getTarget(), e.getPhoneNumber()}) {
                if (key.equals(CorrelationEngine.phoneKey(field))) {
                    return true;
                }
            }
            return haystack.replaceAll("\\D", "").contains(key);
        }
        return false;
    }

    private static List<Long> validIds(JsonNode array, Set<Long> allowed) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode id : array) {
            long value = id.asLong(-1);
            if (allowed.contains(value) && !ids.contains(value)) {
                ids.add(value);
            }
        }
        return ids;
    }

    private static String join(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
}
