package com.lendup.catalogo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

/**
 * Documenta el API; las reglas de autenticación están en CatalogoSecurityConfig.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "LendUp - API de Catálogo",
        version = "v1",
        description = "Categorías, publicaciones, disponibilidad ofrecida e imágenes. "
                + "Las escrituras exigen un Firebase ID token válido y un perfil en Identidad."))
@SecurityScheme(name = "firebaseBearer", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "Firebase ID token",
        description = "Token Firebase del estudiante, verificado contra Identidad")
public class CatalogoOpenApiConfig {
}
