import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;

public class TotpUtil {

    // Public API
    public static String generateTotpCode(String hexSeed) throws GeneralSecurityException {
        byte[] key = hexToBytes(validateHexSeed(hexSeed));
        return generateTotpFromKey(key, 30, 6);
    }

    public static boolean verifyTotpCode(String hexSeed, String code, int validWindow) throws GeneralSecurityException {
        byte[] key = hexToBytes(validateHexSeed(hexSeed));
        long nowSeconds = Instant.now().getEpochSecond();
        long period = 30L;
        long t = nowSeconds / period;
        for (int i = -validWindow; i <= validWindow; i++) {
            String candidate = generateTotpFromKey(key, period, 6, t + i);
            if (candidate.equals(code)) return true;
        }
        return false;
    }

    // Overload to allow specifying period/digits if needed
    public static String generateTotpFromKey(byte[] key, long periodSeconds, int digits) throws GeneralSecurityException {
        long t = Instant.now().getEpochSecond() / periodSeconds;
        return generateTotpFromKey(key, periodSeconds, digits, t);
    }

    // core TOTP implementation using HMAC-SHA1
    private static String generateTotpFromKey(byte[] key, long periodSeconds, int digits, long counter) throws GeneralSecurityException {
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array(); // big-endian
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(msg);

        // dynamic truncation
        int offset = hash[hash.length - 1] & 0x0F;
        int binary =
                ((hash[offset] & 0x7f) << 24) |
                ((hash[offset + 1] & 0xff) << 16) |
                ((hash[offset + 2] & 0xff) << 8) |
                ((hash[offset + 3] & 0xff));
        int otp = binary % (int) Math.pow(10, digits);
        String result = Integer.toString(otp);
        // zero-pad
        while (result.length() < digits) result = "0" + result;
        return result;
    }

    // Validate hex seed and return normalized lowercase hex
    private static String validateHexSeed(String hexSeed) {
        if (hexSeed == null) throw new IllegalArgumentException("hexSeed is null");
        // normalize: remove whitespace and CRs, toLower
        String s = hexSeed.replaceAll("\\s+", "").replace("\r", "").replace("\n", "").toLowerCase(Locale.ROOT);

        // diagnostics
        if (s.length() != 64) {
            throw new IllegalArgumentException("hexSeed must be exactly 64 hex characters, got length=" + s.length() + ". Value (first 80 chars): "
                    + (s.length() <= 80 ? s : s.substring(0, 80) + "..."));
        }
        if (!s.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("hexSeed contains invalid characters; must be 0-9a-f. Value: " + s);
        }
        return s;
    }

    // Convert hex string to bytes (expects validated 64-char hex)
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    // RFC4648 Base32 encoding (no padding). Useful if you want the base32 secret string.
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    public static String toBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int index = 0, currByte = 0, nextByte = 0;
        int digit;
        int i = 0;
        while (i < data.length) {
            currByte = data[i] & 0xff;
            if (index > 3) {
                if ((i + 1) < data.length) nextByte = data[i + 1] & 0xff;
                else nextByte = 0;
                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= (nextByte >> (8 - index));
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) i++;
            }
            sb.append(BASE32_ALPHABET[digit]);
        }
        return sb.toString();
    }

    // Simple self-test / example usage
    public static void main(String[] args) throws Exception {
        // Example: use the seed you decrypted earlier
        String hexSeed = "7e0535bce1e728630e30ab89cd4c3a9ae953fa7585f52a736ae73b6c71eca5cc";
        System.out.println("Hex seed length = " + hexSeed.length());
        System.out.println("Hex seed (trimmed) = '" + hexSeed.trim() + "'");

        // normalize and validate
        String normalized = validateHexSeed(hexSeed);
        System.out.println("Normalized (len=" + normalized.length() + "): " + normalized);

        System.out.println("Base32 seed: " + toBase32(hexToBytes(normalized)));
        String code = generateTotpCode(normalized);
        System.out.println("Current TOTP: " + code);
        System.out.println("Verify (window=1): " + verifyTotpCode(normalized, code, 1));
    }
}
