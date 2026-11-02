package com.lendup.catalogo.application.port.out;

import java.util.UUID;

/**
 * Resuelve el UUID interno de Identidad a partir del token de usuario
 * ya validado y de su UID. Nunca lee la BD de otro bounded context.
 */
public interface IdentidadUsuarioPort {
    UUID resolverUsuario(String firebaseUid, String idToken);
}
