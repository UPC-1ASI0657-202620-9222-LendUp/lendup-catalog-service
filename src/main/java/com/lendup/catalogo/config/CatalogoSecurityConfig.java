package com.lendup.catalogo.config;

import jakarta.servlet.http.HttpServletResponse;
import com.lendup.catalogo.application.port.out.FirebaseTokenVerifier;
import com.lendup.catalogo.application.port.out.IdentidadUsuarioPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Permite lectura pública y protege escrituras con un principal interno de Identidad.
 * Los adaptadores sólo existen al activar la integración real Firebase.
 */
@Configuration
public class CatalogoSecurityConfig {
    @Bean
    SecurityFilterChain catalogoFilterChain(HttpSecurity http,
            ObjectProvider<FirebaseTokenVerifier> verificadores,
            ObjectProvider<IdentidadUsuarioPort> identidades) throws Exception {
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
                .addFilterBefore(new FirebaseCatalogoAuthenticationFilter(verificadores, identidades),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
