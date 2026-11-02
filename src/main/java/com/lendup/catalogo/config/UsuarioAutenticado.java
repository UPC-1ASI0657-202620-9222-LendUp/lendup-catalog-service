package com.lendup.catalogo.config;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Principal confiable asignado SOLO por el futuro adaptador de Firebase + Identidad.
 * No se construye a partir de X-User-Id ni de parámetros de peticiones HTTP.
 */
public record UsuarioAutenticado(UUID usuarioId) implements Principal {
    public UsuarioAutenticado {
        Objects.requireNonNull(usuarioId, "usuarioId");
    }

    @Override
    public String getName() {
        return usuarioId.toString();
    }
}
