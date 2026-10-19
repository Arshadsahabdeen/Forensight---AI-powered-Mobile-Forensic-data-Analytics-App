package com.forensics.platform.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceStorageServiceTest {

    @TempDir
    Path root;

    private EvidenceStorageService.StoredFile store(EvidenceStorageService service, String content) throws Exception {
        return service.store(7L, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "xml");
    }

    @Test
    void storesContentAddressedReadOnlyFilesWithTheirDigest() throws Exception {
        EvidenceStorageService service = new EvidenceStorageService(root.toString(), 1024);
        var stored = store(service, "<UFDR/>");
        assertThat(stored.storageKey()).isEqualTo("case-7/" + stored.sha256() + ".xml");
        assertThat(service.sha256(stored.storageKey())).isEqualTo(stored.sha256());
        assertThat(Files.isWritable(service.resolve(stored.storageKey()))).isFalse();
        assertThat(store(service, "<UFDR/>").storageKey()).isEqualTo(stored.storageKey());
    }

    @Test
    void neverOverwritesAStoredFileThatNoLongerMatchesItsAddress() throws Exception {
        EvidenceStorageService service = new EvidenceStorageService(root.toString(), 1024);
        var first = store(service, "<UFDR/>");
        Path original = service.resolve(first.storageKey());
        original.toFile().setWritable(true);
        Files.writeString(original, "tampered");

        var second = store(service, "<UFDR/>");

        assertThat(second.storageKey()).isNotEqualTo(first.storageKey()).startsWith("case-7/" + first.sha256() + "-");
        assertThat(service.sha256(second.storageKey())).isEqualTo(first.sha256());
        assertThat(Files.readString(original)).isEqualTo("tampered");
    }

    @Test
    void rejectsEmptyAndOversizedUploadsAndPathEscapes() {
        EvidenceStorageService service = new EvidenceStorageService(root.toString(), 4);
        assertThatThrownBy(() -> store(service, "")).hasMessageContaining("empty");
        assertThatThrownBy(() -> store(service, "too large")).hasMessageContaining("maximum");
        assertThatThrownBy(() -> service.resolve("../../outside.xml")).isInstanceOf(IllegalArgumentException.class);
    }
}
