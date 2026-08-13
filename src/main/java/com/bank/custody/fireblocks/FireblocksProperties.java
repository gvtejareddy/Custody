package com.bank.custody.fireblocks;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fireblocks")
public class FireblocksProperties {
    private String basePath;
    private String apiKey;
    private String secretKey;
    private String privateKeyPath;
    private Webhook webhook = new Webhook();

    public static class Webhook {
        private String signingSecret;
        private String publicKeyPath;
        private String verificationMethod = "HMAC_SHA256"; // or RSA_PEM

        public String getSigningSecret() { return signingSecret; }
        public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
        public String getPublicKeyPath() { return publicKeyPath; }
        public void setPublicKeyPath(String publicKeyPath) { this.publicKeyPath = publicKeyPath; }
        public String getVerificationMethod() { return verificationMethod; }
        public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }
    }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
    public Webhook getWebhook() { return webhook; }
    public void setWebhook(Webhook webhook) { this.webhook = webhook; }
}
