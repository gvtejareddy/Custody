package com.bank.custody.fireblocks;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(FireblocksProperties.class)
public class FireblocksConfig {

    @Bean
    public WebClient fireblocksWebClient(FireblocksProperties props) {
        WebClient.Builder b = WebClient.builder();
        if (props.getBasePath() != null && !props.getBasePath().isBlank()) {
            b.baseUrl(props.getBasePath());
        }
        return b.build();
    }
}
