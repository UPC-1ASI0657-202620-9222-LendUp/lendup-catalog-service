package com.lendup.catalogo.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fail-closed: aún NO existe un autenticador de Firebase/Identidad en este servicio.
 * Se permite solo lectura pública; NO se habilita Basic, login ni confianza en headers.
 * El paso de autenticación posterior emitirá UsuarioAutenticado validado.
 */
@Configuration
public class CatalogoSecurityConfig {
    @Bean
    SecurityFilterChain catalogoFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/catalogo/categorias",
                                "/api/v1/catalogo/categorias/{id}",
                                "/api/v1/catalogo/publicaciones",
                                "/api/v1/catalogo/publicaciones/{id}").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
