package web.tosunsaeng.domain.learningrecorddeletion.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class DeletionIdentifiers {
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private DeletionIdentifiers() {}

    public static String userId(String value) {
        if (value == null || !UUID.matcher(value).matches()) {
            throw new IllegalArgumentException("Canonical user UUID is required");
        }
        return value;
    }

    public static String uuidV4(String value) {
        if (value == null || !KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Canonical lowercase UUID v4 is required");
        }
        return value;
    }

    public static String commandId(String userId, String key) {
        return sha256(userId(userId) + ":" + uuidV4(key));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
