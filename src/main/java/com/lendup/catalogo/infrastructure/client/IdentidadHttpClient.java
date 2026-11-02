package com.lendup.catalogo.infrastructure.client;

import com.lendup.catalogo.application.exception.IdentidadNoDisponibleException;
import com.lendup.catalogo.application.exception.IdentidadNoRegistradaException;
import com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException;
import com.lendup.catalogo.application.port.out.IdentidadUsuarioPort;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Contrato comprobado en el monolito de Identidad:
 * GET /api/v1/estudiantes/me -> {"usuario":{"id":"<UUID>"},"perfil":{...}}.
 * Identidad deriva el usuario del token reenviado, no del UID sin verificar.
 */
@Component
@ConditionalOnProperty(prefix = "catalogo.security", name = "firebase-enabled", havingValue = "true")
public class IdentidadHttpClient implements IdentidadUsuarioPort {
    private final RestClient http;

    public IdentidadHttpClient(@Value("${identity.base-url:}") String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("identity.base-url es obligatorio si Firebase está habilitado");
        }
        URI uri = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())))) {
            throw new IllegalStateException("Identidad requiere HTTPS; HTTP solo se permite en localhost");
        }
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(4000);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica).build();
    }

    public record IdentidadMe(Usuario usuario) {}
    public record Usuario(UUID id) {}

    @Override
    public UUID resolverUsuario(String firebaseUid, String idToken) {
        if (firebaseUid == null || firebaseUid.isBlank() || idToken == null || idToken.isBlank()) {
            throw new TokenFirebaseInvalidoException();
        }
        try {
            IdentidadMe respuesta = http.get()
                    .uri("/api/v1/estudiantes/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken)
                    .retrieve()
                    .body(IdentidadMe.class);
            if (respuesta == null || respuesta.usuario() == null || respuesta.usuario().id() == null) {
                throw new IdentidadNoDisponibleException();
            }
            return respuesta.usuario().id();
        } catch (RestClientResponseException ex) {
            HttpStatusCode estado = ex.getStatusCode();
            if (estado.value() == 401) throw new TokenFirebaseInvalidoException();
            if (estado.value() == 403 || estado.value() == 404) throw new IdentidadNoRegistradaException();
            throw new IdentidadNoDisponibleException();
        } catch (ResourceAccessException ex) {
            throw new IdentidadNoDisponibleException();
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IdentidadNoDisponibleException();
        }
    }
}
