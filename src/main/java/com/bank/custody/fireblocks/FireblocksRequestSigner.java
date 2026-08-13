package com.bank.custody.fireblocks;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class FireblocksRequestSigner {
    private final FireblocksProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public FireblocksRequestSigner(FireblocksProperties props) {
        this.props = props;
    }

    public String sign(Object payload) {
        try {
            String secret = props.getSecretKey();
            if (secret == null || secret.isBlank()) return null;
            String json = mapper.writeValueAsString(payload);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            return null;
        }
    }
}
