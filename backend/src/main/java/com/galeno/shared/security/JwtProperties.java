package com.galeno.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hoy la API emite y valida sus propios JWT (HS256). Al pasar a Cognito/Keycloak solo cambia
 * el {@code JwtDecoder} (issuer-uri) y desaparece el login propio.
 */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {
}
