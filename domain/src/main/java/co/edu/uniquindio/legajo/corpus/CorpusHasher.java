package co.edu.uniquindio.legajo.corpus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fixed sha256 hashing rules: {@code java.security.MessageDigest} is a
 * plain JDK API, not a framework, so it is safe to use from {@code domain}.
 *
 * <p>{@code abstractSha256} is the sha256 hex digest of the UTF-8 abstract text.
 * {@code corpusSha256} is the sha256 hex digest of the UTF-8 text obtained by
 * concatenating {@code id + ":" + abstractSha256 + "\n"} for every document, taken in
 * ascending {@code id} order — regardless of the input list's order.
 */
public final class CorpusHasher {

    private CorpusHasher() {
    }

    public static String sha256Hex(String text) {
        byte[] hash = digest().digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    public static String abstractSha256(String abstractText) {
        return sha256Hex(abstractText);
    }

    public static String corpusSha256(List<CorpusDocument> documents) {
        String concatenation = documents.stream()
                .sorted(Comparator.comparing(CorpusDocument::id))
                .map(document -> document.id() + ":" + document.abstractSha256() + "\n")
                .collect(Collectors.joining());
        return sha256Hex(concatenation);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JCA standard names); this is unreachable.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
