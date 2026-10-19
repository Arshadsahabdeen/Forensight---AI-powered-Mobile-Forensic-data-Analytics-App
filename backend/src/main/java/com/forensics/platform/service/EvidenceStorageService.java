package com.forensics.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores original evidence files on the local filesystem.
 *
 * <ul>
 *   <li>The SHA-256 digest is computed while the upload is streamed, before anything else reads it.</li>
 *   <li>Files are content-addressed ({@code case-<id>/<sha256>.<ext>}); user-supplied names are never
 *       used in paths, so path traversal through file names is impossible.</li>
 *   <li>An existing stored file is never overwritten, and stored files are marked read-only.</li>
 * </ul>
 */
@Service
public class EvidenceStorageService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EvidenceStorageService.class);

    private final Path root;
    private final long maxBytes;

    public EvidenceStorageService(
            @Value("${app.storage.evidence-root:./data/evidence}") String root,
            @Value("${app.upload.max-bytes:104857600}") long maxBytes
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public record StoredFile(String storageKey, String sha256, long sizeBytes) {}

    public long maxBytes() {
        return maxBytes;
    }

    public StoredFile store(Long caseId, InputStream input, String extension) throws IOException {
        Path tempDir = Files.createDirectories(root.resolve("tmp"));
        Path temp = tempDir.resolve(UUID.randomUUID() + ".part");
        MessageDigest digest = sha256Digest();
        long size = 0;
        try {
            try (InputStream in = new DigestInputStream(input, digest); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    size += read;
                    if (size > maxBytes) {
                        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the maximum allowed size");
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (size == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            String key = "case-" + caseId + "/" + sha256 + "." + extension;
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            if (Files.exists(target) && !sha256.equals(sha256(key))) {
                // A file already occupies this content address but no longer matches it (corruption or
                // tampering). Never overwrite it: it may itself be evidence. Store the new upload separately.
                log.warn("Stored file at {} does not match its content address; keeping it and storing the new upload separately", key);
                key = "case-" + caseId + "/" + sha256 + "-" + UUID.randomUUID() + "." + extension;
                target = resolve(key);
            }
            if (Files.exists(target)) {
                // Identical content already stored (same digest, verified above); keep the original untouched.
                Files.deleteIfExists(temp);
            } else {
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temp, target);
                }
                target.toFile().setReadOnly();
            }
            return new StoredFile(key, sha256, size);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Resolves a storage key to a path inside the evidence root, rejecting anything that escapes it.
     */
    public Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the evidence root");
        }
        return path;
    }

    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    public String sha256(String storageKey) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = new DigestInputStream(Files.newInputStream(resolve(storageKey)), digest)) {
            byte[] buffer = new byte[64 * 1024];
            while (in.read(buffer) != -1) {
                // digest updated by the stream
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
