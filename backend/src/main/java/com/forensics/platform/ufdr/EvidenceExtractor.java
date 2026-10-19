package com.forensics.platform.ufdr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts evidence records from the platform's supported XML schema (see datasets/samples/README.md).
 *
 * <p>A record is an element whose name is a known record tag (sms, call, contact, ...), that has
 * child elements, and that is not nested inside another record. This prevents body-text elements
 * such as {@code <sms><message>text</message></sms>} from being mistaken for records, and keeps
 * nested fields (for example a photo's {@code <location>}) attached to their parent record.</p>
 *
 * <p>Field values are read only from leaf elements so container text is never concatenated.</p>
 */
public final class EvidenceExtractor {

    public record DeviceInfo(String manufacturer, String brand, String model, String imei,
                             String serialNumber, String operatingSystem, String osVersion) {
        public boolean isEmpty() {
            return manufacturer == null && brand == null && model == null && imei == null
                    && serialNumber == null && operatingSystem == null && osVersion == null;
        }
    }

    public record ExtractedRecord(
            String artifactType,
            String title,
            String content,
            String source,
            String target,
            String phoneNumber,
            String contactName,
            String location,
            Double latitude,
            Double longitude,
            TimestampParser.ParsedTimestamp timestamp,
            String sourceRecordRef
    ) {}

    public record ExtractionResult(String rootElement, DeviceInfo device, List<ExtractedRecord> records, List<String> warnings) {}

    private static final Map<String, String> RECORD_TAGS = Map.ofEntries(
            Map.entry("sms", "SMS"),
            Map.entry("message", "SMS"),
            Map.entry("chat", "CHAT"),
            Map.entry("email", "EMAIL"),
            Map.entry("call", "CALL"),
            Map.entry("contact", "CONTACT"),
            Map.entry("location", "LOCATION"),
            Map.entry("photo", "PHOTO"),
            Map.entry("video", "VIDEO"),
            Map.entry("browser", "BROWSER"),
            Map.entry("browserhistory", "BROWSER_HISTORY")
    );

    private static final int MAX_FIELD_LENGTH = 20_000;

    private EvidenceExtractor() {
    }

    public static ExtractionResult extract(Document document) {
        Element root = document.getDocumentElement();
        List<ExtractedRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DeviceInfo device = extractDevice(root);
        collect(root, records, warnings);
        if (records.isEmpty()) {
            warnings.add("No supported evidence records were found in the XML document.");
        }
        return new ExtractionResult(root.getNodeName(), device, records, warnings);
    }

    private static DeviceInfo extractDevice(Element root) {
        Element device = firstChildElement(root, "device");
        if (device == null) {
            device = firstDescendant(root, "device");
        }
        if (device == null) {
            return new DeviceInfo(null, null, null, null, null, null, null);
        }
        return new DeviceInfo(
                leaf(device, "manufacturer"),
                leaf(device, "brand"),
                leaf(device, "model"),
                leaf(device, "imei"),
                leaf(device, "serialNumber", "serial"),
                leaf(device, "operatingSystem", "os", "platform"),
                leaf(device, "osVersion", "version")
        );
    }

    private static void collect(Element element, List<ExtractedRecord> records, List<String> warnings) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) {
                continue;
            }
            String type = RECORD_TAGS.get(child.getNodeName().toLowerCase(Locale.ROOT));
            if (type != null && hasChildElements(child)) {
                records.add(toRecord(child, type));
            } else if (!"device".equalsIgnoreCase(child.getNodeName())) {
                // Descend into container elements such as <messages> or <calls>.
                collect(child, records, warnings);
            }
        }
    }

    private static ExtractedRecord toRecord(Element element, String type) {
        String timestampText = leaf(element, "timestamp", "datetime", "dateTime", "eventTime", "time", "date", "sent", "created");
        String zone = leaf(element, "timezone", "tz", "utcOffset");
        TimestampParser.ParsedTimestamp timestamp = TimestampParser.parse(timestampText, zone);

        String sender = leaf(element, "sender", "from", "source");
        String receiver = leaf(element, "receiver", "recipient", "to", "target");
        String phone = leaf(element, "phone", "phoneNumber", "number", "telephone", "mobile");
        String location = leaf(element, "address", "location", "place");
        Double latitude = parseDouble(leaf(element, "latitude", "lat"));
        Double longitude = parseDouble(leaf(element, "longitude", "lon", "lng", "long"));
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            latitude = null;
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            longitude = null;
        }

        String title;
        String content;
        String contactName;

        switch (type) {
            case "CONTACT" -> {
                contactName = leaf(element, "name", "contactName", "displayName");
                title = contactName;
                content = leaf(element, "email", "note", "organization");
            }
            case "CALL" -> {
                contactName = leaf(element, "contactName", "contact");
                String direction = leaf(element, "direction", "type");
                String duration = leaf(element, "duration");
                title = callTitle(direction, duration);
                content = leaf(element, "note", "content");
            }
            case "PHOTO", "VIDEO" -> {
                contactName = null;
                title = leaf(element, "fileName", "filename", "name", "title", "path");
                content = leaf(element, "caption", "description", "content");
            }
            case "BROWSER", "BROWSER_HISTORY" -> {
                contactName = null;
                title = leaf(element, "title", "name");
                content = leaf(element, "url", "content", "value");
            }
            case "EMAIL" -> {
                contactName = leaf(element, "contactName", "contact");
                title = leaf(element, "subject", "title");
                content = leaf(element, "body", "message", "text", "content");
            }
            case "CHAT" -> {
                contactName = leaf(element, "contactName", "contact");
                String app = leaf(element, "app", "application", "service");
                title = app == null ? leaf(element, "title", "subject") : app + " message";
                content = leaf(element, "message", "text", "body", "content");
            }
            default -> {
                contactName = leaf(element, "contactName", "contact");
                title = leaf(element, "title", "subject");
                content = leaf(element, "message", "text", "body", "content", "value");
            }
        }

        return new ExtractedRecord(
                type,
                limit(title, 4000),
                limit(content, MAX_FIELD_LENGTH),
                limit(sender, 255),
                limit(receiver, 255),
                limit(phone, 255),
                limit(contactName, 255),
                limit(location, 255),
                latitude,
                longitude,
                timestamp,
                limit(path(element), 255)
        );
    }

    private static String callTitle(String direction, String duration) {
        String label = direction == null ? "Call" : capitalize(direction) + " call";
        if (duration != null && duration.matches("\\d+")) {
            label += " (" + duration + " s)";
        }
        return label;
    }

    /**
     * XPath-like location of the element in the source document, e.g. {@code /UFDR/sms[2]}.
     */
    static String path(Element element) {
        StringBuilder path = new StringBuilder();
        Node current = element;
        while (current instanceof Element el) {
            int index = 1;
            Node sibling = el.getPreviousSibling();
            while (sibling != null) {
                if (sibling instanceof Element other && other.getNodeName().equals(el.getNodeName())) {
                    index++;
                }
                sibling = sibling.getPreviousSibling();
            }
            boolean isRoot = !(el.getParentNode() instanceof Element);
            path.insert(0, "/" + el.getNodeName() + (isRoot ? "" : "[" + index + "]"));
            current = el.getParentNode();
        }
        return path.toString();
    }

    /**
     * First non-blank text of a leaf descendant whose name matches one of {@code names} (case-insensitive),
     * checking names in priority order.
     */
    private static String leaf(Element parent, String... names) {
        for (String name : names) {
            String value = findLeaf(parent, name.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String findLeaf(Element parent, String lowerName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) {
                continue;
            }
            if (child.getNodeName().toLowerCase(Locale.ROOT).equals(lowerName) && !hasChildElements(child)) {
                String text = child.getTextContent();
                if (text != null && !text.isBlank()) {
                    return text.trim().replaceAll("\\s+", " ");
                }
            }
        }
        // Nested fields (e.g. <photo><location><latitude>) are read after direct children.
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && hasChildElements(child)) {
                String value = findLeaf(child, lowerName);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean hasChildElements(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getNodeName().equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }

    private static Element firstDescendant(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el && el.getNodeName().equalsIgnoreCase(name)) {
                return el;
            }
        }
        return null;
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
