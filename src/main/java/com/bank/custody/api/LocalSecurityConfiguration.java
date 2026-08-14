package com.bank.custody.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Local-only bearer-token stub, to be replaced by bank IAM during deployment. */
@Configuration
@Profile("!test")
public class LocalSecurityConfiguration {
    @Bean
    LocalBearerTokenFilter localBearerTokenFilter(@Value("${security.local-jwt.token}") String token) {
        return new LocalBearerTokenFilter(token);
    }

    @Bean
    FilterRegistrationBean<LocalBearerTokenFilter> localBearerTokenFilterRegistration(LocalBearerTokenFilter tokenFilter) {
        FilterRegistrationBean<LocalBearerTokenFilter> registration = new FilterRegistrationBean<>(tokenFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, LocalBearerTokenFilter tokenFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    static class LocalBearerTokenFilter extends OncePerRequestFilter {
        private final String token;

        LocalBearerTokenFilter(String token) {
            this.token = token;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (("Bearer " + token).equals(request.getHeader("Authorization"))) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "local-dev", null, AuthorityUtils.createAuthorityList("ROLE_CUSTODY_OPERATOR"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
