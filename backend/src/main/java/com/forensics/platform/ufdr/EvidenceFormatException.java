package com.forensics.platform.ufdr;

/**
 * The evidence file is malformed, unsupported or unsafe. The message is safe to show to users:
 * it never contains evidence content.
 */
public class EvidenceFormatException extends Exception {
    public EvidenceFormatException(String message) {
        super(message);
    }
}
