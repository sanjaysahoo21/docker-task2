import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

public class TotpApiServer {
    private static final Path SEED_PATH = Path.of("/data/seed.txt");
    // Try multiple locations: container absolute (/app/...), repo-relative (app/student_private.pem),
// and repo root (student_private.pem). Use the first that exists.
    private static final Path PRIVATE_KEY_PATH = findPrivateKeyPath();

    private static Path findPrivateKeyPath() {
        Path[] candidates = new Path[] {
            Path.of("/app/student_private.pem"),      // container runtime
            Path.of("app/student_private.pem"),      // local dev (app folder)
            Path.of("student_private.pem")           // local dev (repo root)
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        // fallback to first option (will cause the same NoSuchFileException later if nothing found)
        return candidates[0];
    }


    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/decrypt-seed", TotpApiServer::handleDecryptSeed);
        server.createContext("/generate-2fa", TotpApiServer::handleGenerate);
        server.createContext("/verify-2fa", TotpApiServer::handleVerify);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        System.out.println("TOTP API server running on port " + port);
        server.start();
    }

    // POST /decrypt-seed { "encrypted_seed": "BASE64..." }
    private static void handleDecryptSeed(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
        try {
            String body = readRequestBody(ex.getRequestBody());
            String enc = extractJsonField(body, "encrypted_seed");
            if (enc == null || enc.isEmpty()) { sendJson(ex,400,"{\"error\":\"Missing encrypted_seed\"}"); return; }

            // load private key
            PrivateKey priv = loadPrivateKey(PRIVATE_KEY_PATH);

            // decrypt
            byte[] cipherBytes = Base64.getDecoder().decode(enc.trim());
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, priv);
            byte[] plain = cipher.doFinal(cipherBytes);
            String seed = new String(plain, StandardCharsets.UTF_8).trim().toLowerCase();

            if (!seed.matches("[0-9a-f]{64}")) {
                sendJson(ex,500,"{\"error\":\"Decryption failed\"}");
                return;
            }

            // write to /data/seed.txt
            if (SEED_PATH.getParent() != null) Files.createDirectories(SEED_PATH.getParent());
            Files.writeString(SEED_PATH, seed, StandardCharsets.UTF_8);

            sendJson(ex,200,"{\"status\":\"ok\"}");
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex,500,"{\"error\":\"Decryption failed\"}");
        }
    }

    // GET /generate-2fa
    private static void handleGenerate(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
        try {
            if (!Files.exists(SEED_PATH)) { sendJson(ex,500,"{\"error\":\"Seed not decrypted yet\"}"); return; }
            String seed = Files.readString(SEED_PATH).trim();
            String code = TotpUtil.generateTotpCode(seed);
            long now = System.currentTimeMillis() / 1000L;
            int validFor = (int)(30 - (now % 30));
            if (validFor == 0) validFor = 30; // when at boundary
            String json = String.format("{\"code\":\"%s\",\"valid_for\":%d}", code, validFor);
            sendJson(ex,200,json);
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex,500,"{\"error\":\"Seed not decrypted yet\"}");
        }
    }

    // POST /verify-2fa { "code": "123456" }
    private static void handleVerify(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex,405,"{\"error\":\"Method not allowed\"}"); return; }
        try {
            String body = readRequestBody(ex.getRequestBody());
            String code = extractJsonField(body, "code");
            if (code == null || code.isEmpty()) { sendJson(ex,400,"{\"error\":\"Missing code\"}"); return; }
            if (!Files.exists(SEED_PATH)) { sendJson(ex,500,"{\"error\":\"Seed not decrypted yet\"}"); return; }
            String seed = Files.readString(SEED_PATH).trim();
            boolean ok = TotpUtil.verifyTotpCode(seed, code.trim(), 1);
            sendJson(ex,200,ok?"{\"valid\":true}" : "{\"valid\":false}");
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex,500,"{\"error\":\"Seed not decrypted yet\"}");
        }
    }

    // Utilities
    private static String readRequestBody(InputStream is) throws IOException {
        byte[] all = is.readAllBytes();
        return new String(all, StandardCharsets.UTF_8);
    }

    // very small JSON extractor for simple payloads
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static PrivateKey loadPrivateKey(Path pemPath) throws Exception {
    if (!Files.exists(pemPath)) {
        throw new IOException("Private key not found at: " + pemPath.toAbsolutePath() +
            ". Checked multiple locations; place student_private.pem in app/ or repo root.");
    }
    String pem = Files.readString(pemPath, StandardCharsets.UTF_8);
    String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
    byte[] der = Base64.getDecoder().decode(normalized);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
    return KeyFactory.getInstance("RSA").generatePrivate(spec);
}

}
