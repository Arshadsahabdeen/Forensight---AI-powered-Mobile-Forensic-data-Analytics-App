package com.forensics.platform.service;

import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvidenceImportService {

    private final EvidenceRepository evidenceRepository;

    public EvidenceImportService(
            EvidenceRepository evidenceRepository
    ) {
        this.evidenceRepository = evidenceRepository;
    }

    public List<Evidence> extractAndSave(
            Document document,
            Device device,
            String fileName
    ) {

        List<Evidence> results = new ArrayList<>();

        if (document == null) {
            return results;
        }

        extract(
                document,
                "sms",
                "SMS",
                device,
                fileName,
                results
        );

        extract(
                document,
                "message",
                "SMS",
                device,
                fileName,
                results
        );

        extract(
                document,
                "call",
                "CALL",
                device,
                fileName,
                results
        );

        extract(
                document,
                "contact",
                "CONTACT",
                device,
                fileName,
                results
        );

        extract(
                document,
                "location",
                "LOCATION",
                device,
                fileName,
                results
        );

        extract(
                document,
                "photo",
                "PHOTO",
                device,
                fileName,
                results
        );

        extract(
                document,
                "video",
                "VIDEO",
                device,
                fileName,
                results
        );

        extract(
                document,
                "browser",
                "BROWSER",
                device,
                fileName,
                results
        );

        extract(
                document,
                "browserHistory",
                "BROWSER_HISTORY",
                device,
                fileName,
                results
        );

        if (!results.isEmpty()) {
            results = evidenceRepository.saveAll(results);
        }

        return results;
    }

    private void extract(
            Document document,
            String tagName,
            String artifactType,
            Device device,
            String fileName,
            List<Evidence> output
    ) {

        NodeList nodes =
                document.getElementsByTagName(tagName);

        for (int i = 0; i < nodes.getLength(); i++) {

            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;

            String title =
                    firstValue(
                            element,
                            "title",
                            "name",
                            "subject"
                    );

            String content =
                    firstValue(
                            element,
                            "message",
                            "text",
                            "body",
                            "content",
                            "value"
                    );

            String sender =
                    firstValue(
                            element,
                            "sender",
                            "from",
                            "source"
                    );

            String receiver =
                    firstValue(
                            element,
                            "receiver",
                            "recipient",
                            "to",
                            "target"
                    );

            String phone =
                    firstValue(
                            element,
                            "phone",
                            "phoneNumber",
                            "number",
                            "telephone"
                    );

            String contact =
                    firstValue(
                            element,
                            "contactName",
                            "name",
                            "contact"
                    );

            String location =
                    firstValue(
                            element,
                            "location",
                            "address",
                            "place"
                    );

            Double latitude =
                    parseDouble(
                            firstValue(
                                    element,
                                    "latitude",
                                    "lat"
                            )
                    );

            Double longitude =
                    parseDouble(
                            firstValue(
                                    element,
                                    "longitude",
                                    "lon",
                                    "lng"
                            )
                    );

            String timestamp =
                    firstValue(
                            element,
                            "timestamp",
                            "time",
                            "date",
                            "datetime",
                            "eventTime"
                    );

            Instant eventTime =
                    parseInstant(timestamp);

            /*
             * Check against evidence already stored
             * in the database.
             */
            boolean databaseDuplicate =
                    evidenceRepository.existsDuplicate(
                            device.getId(),
                            artifactType,
                            title,
                            content,
                            sender,
                            receiver,
                            phone,
                            contact,
                            location,
                            eventTime
                    );

            if (databaseDuplicate) {
                continue;
            }

            /*
             * Check against evidence already extracted
             * from the current XML/UFDR file.
             */
            boolean fileDuplicate =
                    output.stream().anyMatch(e ->
                            same(
                                    e.getArtifactType(),
                                    artifactType
                            )
                            && same(
                                    e.getTitle(),
                                    title
                            )
                            && same(
                                    e.getContent(),
                                    content
                            )
                            && same(
                                    e.getSource(),
                                    sender
                            )
                            && same(
                                    e.getTarget(),
                                    receiver
                            )
                            && same(
                                    e.getPhoneNumber(),
                                    phone
                            )
                            && same(
                                    e.getContactName(),
                                    contact
                            )
                            && same(
                                    e.getLocation(),
                                    location
                            )
                            && same(
                                    e.getEventTime(),
                                    eventTime
                            )
                    );

            if (fileDuplicate) {
                continue;
            }

            Evidence evidence = new Evidence();

            evidence.setArtifactType(artifactType);
            evidence.setFileName(fileName);
            evidence.setDevice(device);

            evidence.setTitle(title);
            evidence.setContent(content);

            evidence.setSource(sender);
            evidence.setTarget(receiver);

            evidence.setPhoneNumber(phone);
            evidence.setContactName(contact);

            evidence.setLocation(location);
            evidence.setLatitude(latitude);
            evidence.setLongitude(longitude);

            evidence.setEventTime(eventTime);

            output.add(evidence);
        }
    }

    private boolean same(
            Object a,
            Object b
    ) {

        if (a == null && b == null) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }

    private String firstValue(
            Element element,
            String... names
    ) {

        for (String name : names) {

            NodeList nodes =
                    element.getElementsByTagName(name);

            if (nodes.getLength() == 0) {
                continue;
            }

            String value =
                    nodes.item(0)
                            .getTextContent();

            if (value != null &&
                    !value.isBlank()) {

                return value.trim();
            }
        }

        return null;
    }

    private Double parseDouble(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {
            return null;
        }

        try {

            return Double.parseDouble(
                    value.trim()
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }

    private Instant parseInstant(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {
            return null;
        }

        try {

            return Instant.parse(
                    value.trim()
            );

        } catch (DateTimeParseException e) {

            return null;
        }
    }
}
