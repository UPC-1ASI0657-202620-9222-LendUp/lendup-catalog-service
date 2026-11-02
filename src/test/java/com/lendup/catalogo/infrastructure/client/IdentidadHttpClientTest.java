package com.lendup.catalogo.infrastructure.client;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.catalogo.application.exception.IdentidadNoDisponibleException;
import com.lendup.catalogo.application.exception.IdentidadNoRegistradaException;
import com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentidadHttpClientTest {
    private HttpServer servidor;
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private int estado = 200;
    private String respuesta = "{}";

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ruta.set(exchange.getRequestURI().getPath());
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(estado, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private IdentidadHttpClient cliente() {
        return new IdentidadHttpClient("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @Test
    void recuperaIdInternoDeUsuarioDeContratoExistente() {
        UUID id = UUID.randomUUID();
        respuesta = "{\"usuario\":{\"id\":\"" + id + "\"},\"perfil\":{}}";
        assertEquals(id, cliente().resolverUsuario("uid-de-firebase", "token-seguro"));
        assertEquals("/api/v1/estudiantes/me", ruta.get());
        assertEquals("Bearer token-seguro", authHeader.get());
    }

    @Test
    void noAceptaRespuestaSinUsuarioValido() {
        respuesta = "{\"perfil\":{}}";
        assertThrows(IdentidadNoDisponibleException.class,
                () -> cliente().resolverUsuario("uid", "token"));
    }

    @Test
    void usuarioNoRegistradoRespondeComoAccesoDenegado() {
        estado = 403;
        respuesta = "{\"error\":\"Registra tu perfil\"}";
        assertThrows(IdentidadNoRegistradaException.class,
                () -> cliente().resolverUsuario("uid", "token"));
    }

    @Test
    void tokenRechazadoPorIdentidadNoEsValido() {
        estado = 401;
        respuesta = "{}";
        assertThrows(TokenFirebaseInvalidoException.class,
                () -> cliente().resolverUsuario("uid", "token"));
    }

    @Test
    void identidadConErrorEsNoDisponible() {
        estado = 503;
        respuesta = "{}";
        assertThrows(IdentidadNoDisponibleException.class,
                () -> cliente().resolverUsuario("uid", "token"));
    }

    @Test
    void tokenVacioNuncaSeEnviaALaRed() {
        assertThrows(TokenFirebaseInvalidoException.class,
                () -> cliente().resolverUsuario("uid", ""));
        assertNull(authHeader.get());
    }

    @Test
    void configuracionRemotaRequiereHttps() {
        assertThrows(IllegalStateException.class,
                () -> new IdentidadHttpClient("http://identidad-remota:8080"));
        assertThrows(IllegalStateException.class,
                () -> new IdentidadHttpClient(""));
    }
}
