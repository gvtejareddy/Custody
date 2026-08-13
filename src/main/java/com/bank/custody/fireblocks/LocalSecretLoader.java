package com.bank.custody.fireblocks;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalSecretLoader {
    private final FireblocksProperties props;

    public LocalSecretLoader(FireblocksProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void loadSecrets() {
        // Priority: explicit secretKey property (env) -> privateKeyPath file
        if ((props.getSecretKey() == null || props.getSecretKey().isBlank()) && props.getPrivateKeyPath() != null && !props.getPrivateKeyPath().isBlank()) {
            try {
                String content = Files.readString(Path.of(props.getPrivateKeyPath()));
                if (content != null && !content.isBlank()) {
                    props.setSecretKey(content.trim());
                }
            } catch (Exception ignored) {
                // best-effort for local development; don't fail startup
            }
        }
        // Also allow environment variable FIREBLOCKS_SECRET_KEY to override
        try {
            String env = System.getenv("FIREBLOCKS_SECRET_KEY");
            if (env != null && !env.isBlank()) props.setSecretKey(env.trim());
        } catch (Exception ignored) {}
    }
}
