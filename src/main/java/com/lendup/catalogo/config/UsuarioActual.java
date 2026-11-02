package com.lendup.catalogo.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Aísla los casos de uso de los detalles del contexto Spring Security. */
@Component
public class UsuarioActual {
    public UUID exigirId() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new AccessDeniedException("Se requiere identidad interna verificada");
        }
        return usuario.usuarioId();
    }
}
