package com.galeno.shared.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.galeno.shared.tenancy.TenantFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, SesionVigenteFilter sesionVigenteFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sin cookies: el token viaja en Authorization
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Públicas
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // El token de invitación viaja en el cuerpo, nunca en la URL (no queda en logs)
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitaciones/consultar", "/api/v1/invitaciones/aceptar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registro", "/api/v1/registro/consultar", "/api/v1/registro/confirmar").permitAll()
                        // Buzón de desarrollo: el controlador solo existe con app.correo.proveedor=local
                        .requestMatchers(HttpMethod.GET, "/api/v1/dev/buzon").permitAll()
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Cualquier token válido, incluido el de selección de consultorio
                        .requestMatchers("/api/v1/auth/consultorios", "/api/v1/auth/sesion").authenticated()
                        // Por permiso (los roles solo agrupan permisos, ver identidad.Rol)
                        .requestMatchers("/api/v1/equipo/**").hasAuthority(Permiso.EQUIPO_GESTIONAR.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/formulacion/formulas").hasAuthority(Permiso.FORMULA_FIRMAR.name())
                        .requestMatchers("/api/v1/formulacion/**").hasAuthority(Permiso.FORMULA_REDACTAR.name())
                        .requestMatchers("/api/v1/apoyo-diagnostico/**").hasAuthority(Permiso.IA_DIAGNOSTICO.name())
                        .requestMatchers("/api/v1/ai-jobs/**").hasAnyAuthority(Permiso.FORMULA_REDACTAR.name(), Permiso.IA_DIAGNOSTICO.name())
                        .requestMatchers("/api/v1/pacientes/**").hasAuthority(Permiso.PACIENTE_GESTIONAR.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/historia/**").hasAuthority(Permiso.HISTORIA_VER.name())
                        .requestMatchers("/api/v1/historia/**").hasAuthority(Permiso.HISTORIA_REGISTRAR.name())
                        .requestMatchers("/api/v1/importaciones/**").hasAuthority(Permiso.IMPORTAR_HISTORIAS.name())
                        .requestMatchers("/api/v1/agenda/**").hasAuthority(Permiso.AGENDA_GESTIONAR.name())
                        // El resto exige un token de sesión en un consultorio
                        .anyRequest().hasAuthority(TokenService.MIEMBRO))
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(new TenantFilter(), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(sesionVigenteFilter, TenantFilter.class);
        return http.build();
    }

    @Bean
    SesionVigenteFilter sesionVigenteFilter(SesionVerifier verifier) {
        return new SesionVigenteFilter(verifier);
    }

    /** Evita que Spring Boot registre el filtro también fuera de la cadena de seguridad. */
    @Bean
    FilterRegistrationBean<SesionVigenteFilter> sesionVigenteFilterRegistration(SesionVigenteFilter filter) {
        FilterRegistrationBean<SesionVigenteFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenService.CLAIM_AUTHORITIES);
        roles.setAuthorityPrefix(""); // el claim ya trae ROLE_x y los nombres de permiso
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    private static SecretKey secretKey(JwtProperties props) {
        byte[] bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret debe tener al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
