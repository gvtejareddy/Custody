package com.bank.custody.fireblocks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;
import java.util.Map;

@Component
public class FireblocksWebhookVerifier implements WebhookVerifier {

    private final FireblocksProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public FireblocksWebhookVerifier(FireblocksProperties props) {
        this.props = props;
    }

    @Override
    public boolean verify(Map<String, Object> payload, Map<String, String> headers) {
        try {
            String method = props.getWebhook().getVerificationMethod();
            String signatureHeader = headers.getOrDefault("X-Fireblocks-Signature", headers.get("x-fireblocks-signature"));
            if (signatureHeader == null) return false;

            String body = mapper.writeValueAsString(payload);

            if ("HMAC_SHA256".equalsIgnoreCase(method)) {
                String secret = props.getWebhook().getSigningSecret();
                if (secret == null || secret.isBlank()) return false;
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
                String expected = Base64.getEncoder().encodeToString(sig);
                return constantTimeEquals(expected, signatureHeader);
            } else if ("RSA_PEM".equalsIgnoreCase(method)) {
                String pubPath = props.getWebhook().getPublicKeyPath();
                if (pubPath == null || pubPath.isBlank()) return false;
                byte[] pem = Files.readAllBytes(Paths.get(pubPath));
                String pemStr = new String(pem, StandardCharsets.UTF_8)
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");
                byte[] der = Base64.getDecoder().decode(pemStr);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pub = kf.generatePublic(spec);
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(pub);
                sig.update(body.getBytes(StandardCharsets.UTF_8));
                byte[] sigBytes = Base64.getDecoder().decode(signatureHeader);
                return sig.verify(sigBytes);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int r = 0;
        for (int i = 0; i < x.length; i++) r |= x[i] ^ y[i];
        return r == 0;
    }
}
