package com.forensics.platform.ufdr;

import com.forensics.platform.entity.Device;
import com.forensics.platform.entity.Evidence;
import com.forensics.platform.service.EvidenceNormalizationService;
import com.forensics.platform.service.EvidenceImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UFDRService {

    private final UFDRParser ufdrParser;
    private final UFDRImportService importService;
    private final EvidenceImportService evidenceImportService;
    private final EvidenceNormalizationService evidenceNormalizationService;

    public Object process(MultipartFile file) throws Exception {

        Map<String, Object> data =
                ufdrParser.parse(file);

        /*
         * Import device information.
         */
        Device device =
                importService.importDevice(data);

        /*
         * Extract original XML.
         */
        String rawXml =
                (String) data.get("rawXml");

        int evidenceCount = 0;

        if (rawXml != null && !rawXml.isBlank()) {

            Document document =
                    parseXML(rawXml);

            List<Evidence> evidence =
                    evidenceImportService.extractAndSave(
                            document,
                            device,
                            file.getOriginalFilename()
                    );

            evidenceNormalizationService.normalizeEvidence(
                    evidence,
                    "UFDR"
            );

            evidenceCount = evidence.size();
        }

        Map<String, Object> response =
                new HashMap<>(data);

        /*
         * Never return the entire XML to the frontend.
         */
        response.remove("rawXml");

        response.put(
                "status",
                "SUCCESS"
        );

        response.put(
                "message",
                "UFDR processed successfully"
        );

        response.put(
                "deviceId",
                device.getId()
        );

        response.put(
                "evidenceCount",
                evidenceCount
        );

        return response;
    }

    private Document parseXML(
            String xml
    ) throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        /*
         * Basic XXE protection.
         */
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        return builder.parse(
                new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)
                )
        );
    }
}
