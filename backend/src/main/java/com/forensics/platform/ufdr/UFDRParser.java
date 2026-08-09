package com.forensics.platform.ufdr;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class UFDRParser {

    private final XMLParser xmlParser;

    public Map<String, Object> parse(MultipartFile file) throws Exception {

        String name = file.getOriginalFilename();

        if (name == null) {
            throw new RuntimeException("File name is missing");
        }

        name = name.toLowerCase(Locale.ROOT);

        if (name.endsWith(".xml")) {
            return parseXML(file);
        }

        if (name.endsWith(".ufdr") || name.endsWith(".zip")) {
            return parseArchive(file);
        }

        throw new RuntimeException("Unsupported file type");
    }

    private Map<String, Object> parseXML(
            MultipartFile file
    ) throws Exception {

        Map<String, Object> result = new HashMap<>();

        byte[] bytes = file.getBytes();

        String xml = new String(
                bytes,
                StandardCharsets.UTF_8
        );

        result.put("status", "SUCCESS");
        result.put("fileName", file.getOriginalFilename());
        result.put("size", file.getSize());

        // Keep original XML for evidence extraction
        result.put("rawXml", xml);

        // Existing device/XML parsing
        result.putAll(xmlParser.parse(file));

        return result;
    }

    private Map<String, Object> parseArchive(
            MultipartFile file
    ) throws Exception {

        Path temp = Files.createTempDirectory("ufdr");

        try {

            try (ZipInputStream zis =
                         new ZipInputStream(file.getInputStream())) {

                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {

                    Path output = temp.resolve(entry.getName())
                            .normalize();

                    // Prevent ZIP path traversal
                    if (!output.startsWith(temp)) {
                        continue;
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                        continue;
                    }

                    Path parent = output.getParent();

                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    Files.copy(
                            zis,
                            output,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }

            File xml = findXML(temp.toFile());

            if (xml == null) {
                throw new RuntimeException(
                        "No XML file found inside UFDR archive"
                );
            }

            MultipartFile multipart =
                    new LocalMultipartFile(xml);

            return parseXML(multipart);

        } finally {

            deleteDirectory(temp.toFile());
        }
    }

    private File findXML(File dir) {

        File[] files = dir.listFiles();

        if (files == null) {
            return null;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                File result = findXML(file);

                if (result != null) {
                    return result;
                }

            } else if (
                    file.getName()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".xml")
            ) {

                return file;
            }
        }

        return null;
    }

    private void deleteDirectory(File directory) {

        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files != null) {

            for (File file : files) {

                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }

        directory.delete();
    }
}