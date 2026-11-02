package com.lendup.catalogo.config;

import com.lendup.catalogo.application.exception.IdentidadNoDisponibleException;
import com.lendup.catalogo.application.exception.IdentidadNoRegistradaException;
import com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException;
import com.lendup.catalogo.application.port.out.FirebaseTokenVerifier;
import com.lendup.catalogo.application.port.out.IdentidadUsuarioPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sólo tokens verificados y usuarios resueltos en Identidad obtienen principal interno.
 * Si faltan adaptadores, falla cerrado; no habilita usuarios ficticios ni headers de bypass.
 */
public class FirebaseCatalogoAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectProvider<FirebaseTokenVerifier> verificadores;
    private final ObjectProvider<IdentidadUsuarioPort> identidades;

    public FirebaseCatalogoAuthenticationFilter(
            ObjectProvider<FirebaseTokenVerifier> verificadores,
            ObjectProvider<IdentidadUsuarioPort> identidades) {
        this.verificadores = verificadores;
        this.identidades = identidades;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) return false;
        String ruta = request.getServletPath();
        return ruta.equals("/actuator/health") || ruta.startsWith("/actuator/health/")
                || ruta.equals("/api/v1/catalogo/categorias")
                || ruta.matches("/api/v1/catalogo/categorias/[^/]+")
                || ruta.equals("/api/v1/catalogo/publicaciones")
                || ruta.matches("/api/v1/catalogo/publicaciones/[^/]+");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ") || header.length() <= 7
                || header.substring(7).isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        FirebaseTokenVerifier verificador = verificadores.getIfAvailable();
        IdentidadUsuarioPort identidad = identidades.getIfAvailable();
        if (verificador == null || identidad == null) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        String idToken = header.substring(7);
        try {
            var token = verificador.verificar(idToken);
            if (!token.correoVerificado()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            var usuarioId = identidad.resolverUsuario(token.uid(), idToken);
            var auth = new UsernamePasswordAuthenticationToken(
                    new UsuarioAutenticado(usuarioId), null,
                    List.of(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (TokenFirebaseInvalidoException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IdentidadNoRegistradaException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (IdentidadNoDisponibleException ex) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
