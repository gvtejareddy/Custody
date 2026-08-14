package com.bank.custody.fireblocks;

import com.fireblocks.sdk.BasePath;
import com.fireblocks.sdk.ConfigurationOptions;
import com.fireblocks.sdk.Fireblocks;

/**
 * Simple Fireblocks SDK wrapper. Initializes the SDK and exposes the
 * underlying `Fireblocks` instance for callers to use the generated APIs.
 *
 * NOTE: Keep secret keys out of source control. Provide via env vars
 * or a secure vault in production.
 */
public class FireblocksClient {
    private final Fireblocks fireblocks;

    /**
     * Initialize the Fireblocks client.
     * @param apiKey Fireblocks API key (console)
     * @param secretKey Fireblocks secret key (PEM or raw)
     * @param useSandbox true to use the Fireblocks sandbox base path
     */
    public FireblocksClient(String apiKey, String secretKey, boolean useSandbox) {
        ConfigurationOptions configurationOptions = new ConfigurationOptions()
                .apiKey(apiKey)
                .secretKey(secretKey);

        if (useSandbox) {
            configurationOptions.basePath(BasePath.Sandbox);
        }

        this.fireblocks = new Fireblocks(configurationOptions);
    }

    /**
     * Return the initialized Fireblocks SDK instance so callers can access
     * typed APIs (vaults(), transactions(), webhooks(), etc.).
     */
    public Fireblocks getFireblocks() {
        return fireblocks;
    }
}
