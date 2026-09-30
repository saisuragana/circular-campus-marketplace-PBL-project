import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Passwords are never stored or compared in plain text.
 * They are hashed with SHA-256 and stored as a 64 character hex string.
 *
 * (A production site would also use a per-user salt and a slow hash such as
 * bcrypt. SHA-256 keeps the college project readable while still avoiding
 * plain-text passwords in the database.)
 */
public class PasswordUtil {

    public static String hash(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    /** Constant-time-ish comparison of two hex digests. */
    public static boolean matches(String plain, String storedHash) {
        if (storedHash == null) return false;
        return MessageDigest.isEqual(
                hash(plain).getBytes(StandardCharsets.UTF_8),
                storedHash.trim().getBytes(StandardCharsets.UTF_8));
    }
}
