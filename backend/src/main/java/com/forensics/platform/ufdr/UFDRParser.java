package com.forensics.platform.ufdr;

import com.forensics.platform.entity.EvidenceSourceFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads evidence from a stored original file. XML files are parsed directly; .ufdr/.zip archives
 * are opened through the ZIP central directory so that nothing is extracted to disk (no path
 * traversal) and only the selected XML entry is decompressed, under a strict size limit.
 */
@Component
public class UFDRParser {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final long maxXmlBytes;
    private final int maxArchiveEntries;

    public UFDRParser(
            @Value("${app.ingestion.max-xml-bytes:209715200}") long maxXmlBytes,
            @Value("${app.ingestion.max-archive-entries:20000}") int maxArchiveEntries
    ) {
        this.maxXmlBytes = maxXmlBytes;
        this.maxArchiveEntries = maxArchiveEntries;
    }

    public record ParsedSource(EvidenceExtractor.ExtractionResult extraction, String xmlEntryName, List<String> notes) {}

    /**
     * Determines the source format from the file name and leading bytes.
     */
    public static EvidenceSourceFile.SourceFormat detectFormat(String fileName, byte[] header) throws EvidenceFormatException {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        boolean zipMagic = startsWith(header, ZIP_MAGIC);
        if (lower.endsWith(".xml")) {
            if (zipMagic || !looksLikeXml(header)) {
                throw new EvidenceFormatException("The file has an .xml extension but its content is not XML.");
            }
            return EvidenceSourceFile.SourceFormat.XML;
        }
        if (lower.endsWith(".ufdr") || lower.endsWith(".zip")) {
            if (!zipMagic) {
                throw new EvidenceFormatException("The file has a ." + lower.substring(lower.lastIndexOf('.') + 1)
                        + " extension but is not a ZIP-based archive.");
            }
            return lower.endsWith(".ufdr") ? EvidenceSourceFile.SourceFormat.UFDR : EvidenceSourceFile.SourceFormat.ZIP;
        }
        throw new EvidenceFormatException("Unsupported file type. Upload an .xml, .ufdr or .zip extraction file.");
    }

    public ParsedSource parse(Path storedFile, EvidenceSourceFile.SourceFormat format) throws IOException, EvidenceFormatException {
        if (format == EvidenceSourceFile.SourceFormat.XML) {
            try (InputStream input = new LimitedInputStream(new BufferedInputStream(Files.newInputStream(storedFile)), maxXmlBytes)) {
                Document document = XMLParser.parse(input);
                return new ParsedSource(EvidenceExtractor.extract(document), null, List.of());
            }
        }
        return parseArchive(storedFile);
    }

    private ParsedSource parseArchive(Path storedFile) throws IOException, EvidenceFormatException {
        List<String> notes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(storedFile.toFile())) {
            if (zip.size() > maxArchiveEntries) {
                throw new EvidenceFormatException("The archive contains more than " + maxArchiveEntries + " entries.");
            }
            ZipEntry selected = null;
            int xmlCount = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    continue;
                }
                xmlCount++;
                String baseName = entry.getName().substring(entry.getName().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (baseName.equals("report.xml")) {
                    selected = entry;
                    break;
                }
                if (selected == null) {
                    selected = entry;
                }
            }
            if (selected == null) {
                throw new EvidenceFormatException("No XML report was found inside the archive.");
            }
            if (selected.getSize() > maxXmlBytes) {
                throw new EvidenceFormatException("The XML report inside the archive exceeds the maximum supported size.");
            }
            if (xmlCount > 1) {
                notes.add("Archive contains " + xmlCount + " XML files; parsed '" + safeName(selected.getName()) + "'.");
            }
            try (InputStream input = new LimitedInputStream(new BufferedInputStream(zip.getInputStream(selected)), maxXmlBytes)) {
                Document document = XMLParser.parse(input);
                return new ParsedSource(EvidenceExtractor.extract(document), safeName(selected.getName()), notes);
            }
        } catch (ZipException ex) {
            throw new EvidenceFormatException("The archive is corrupt or not a valid ZIP file.");
        }
    }

    private static String safeName(String name) {
        String cleaned = name.replaceAll("[\\p{Cntrl}]", "");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    private static boolean startsWith(byte[] header, byte[] prefix) {
        if (header == null || header.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (header[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeXml(byte[] header) {
        if (header == null) {
            return false;
        }
        int i = 0;
        // UTF-8 byte order mark
        if (header.length >= 3 && (header[0] & 0xFF) == 0xEF && (header[1] & 0xFF) == 0xBB && (header[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < header.length && Character.isWhitespace(header[i])) {
            i++;
        }
        return i < header.length && header[i] == '<';
    }

    /**
     * Aborts reading once more than {@code limit} bytes have been consumed (decompression-bomb guard).
     */
    static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                check(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0) {
                check(read);
            }
            return read;
        }

        private void check(long read) throws IOException {
            count += read;
            if (count > limit) {
                throw new SizeLimitExceededException();
            }
        }
    }

    public static final class SizeLimitExceededException extends IOException {
        SizeLimitExceededException() {
            super("Evidence XML exceeds the maximum supported size");
        }
    }
}
