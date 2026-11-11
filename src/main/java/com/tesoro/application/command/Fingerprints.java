package com.tesoro.application.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** SHA-256 fingerprint of a request, used to detect an idempotency key reused with another body. */
public final class Fingerprints {

    private Fingerprints() {}

    public static String of(Object... parts) {
        String canonical = Stream.of(parts).map(Objects::toString).collect(Collectors.joining("|"));
        return sha256Hex(canonical);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
