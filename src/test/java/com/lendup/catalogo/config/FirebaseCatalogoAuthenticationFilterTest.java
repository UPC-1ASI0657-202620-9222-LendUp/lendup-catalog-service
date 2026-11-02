package com.lendup.catalogo.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lendup.catalogo.application.exception.IdentidadNoDisponibleException;
import com.lendup.catalogo.application.exception.IdentidadNoRegistradaException;
import com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException;
import com.lendup.catalogo.application.port.out.FirebaseTokenVerifier;
import com.lendup.catalogo.application.port.out.IdentidadUsuarioPort;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class FirebaseCatalogoAuthenticationFilterTest {
    private final FirebaseTokenVerifier firebase = mock(FirebaseTokenVerifier.class);
    private final IdentidadUsuarioPort identidad = mock(IdentidadUsuarioPort.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<FirebaseTokenVerifier> verificadorProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<IdentidadUsuarioPort> identidadProvider = mock(ObjectProvider.class);

    @AfterEach void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest solicitud(String method, String path, String header) {
        var req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        if (header != null) req.addHeader("Authorization", header);
        return req;
    }

    @Test void emitePrincipalConUuidResueltoYNoFirebaseUid() throws Exception {
        when(verificadorProvider.getIfAvailable()).thenReturn(firebase);
        when(identidadProvider.getIfAvailable()).thenReturn(identidad);
        when(firebase.verificar("firebase-token"))
                .thenReturn(new FirebaseTokenVerifier.TokenVerificado("firebase-uid-123", true));
        UUID id = UUID.randomUUID();
        when(identidad.resolverUsuario("firebase-uid-123", "firebase-token")).thenReturn(id);
        var req = solicitud("POST", "/api/v1/catalogo/publicaciones", "Bearer firebase-token");
        req.addHeader("X-User-Id", UUID.randomUUID().toString());
        var res = new MockHttpServletResponse();
        AtomicReference<UUID> capturado = new AtomicReference<>();
        FilterChain chain = (a, b) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(auth.isAuthenticated());
            assertInstanceOf(UsuarioAutenticado.class, auth.getPrincipal());
            capturado.set(((UsuarioAutenticado) auth.getPrincipal()).usuarioId());
        };

        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(req, res, chain);
        assertEquals(id, capturado.get());
        assertEquals(200, res.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void sinTokenNoGeneraIdentidad() throws Exception {
        var req = solicitud("POST", "/api/v1/catalogo/publicaciones", null);
        var res = new MockHttpServletResponse();
        var passed = new java.util.concurrent.atomic.AtomicBoolean();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(req, res, (a,b) -> passed.set(true));
        assertTrue(passed.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(firebase, identidad);
    }

    @Test void formatosNoBearerSonRechazados() throws Exception {
        for (String header : java.util.List.of("Basic abc", "Bearer ", "Bearer    ")) {
            var res = new MockHttpServletResponse();
            var req = solicitud("POST", "/api/v1/catalogo/publicaciones", header);
            new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                    .doFilter(req, res, (a,b) -> fail("No debe pasar"));
            assertEquals(401, res.getStatus());
        }
        verifyNoInteractions(firebase, identidad);
    }

    @Test void sinConfiguracionNoHabilitaUnBypass() throws Exception {
        var res = new MockHttpServletResponse();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(solicitud("POST", "/api/v1/catalogo/publicaciones", "Bearer anything"),
                        res, (a,b) -> fail("No debe pasar"));
        assertEquals(503, res.getStatus());
    }

    @Test void tokenInvalidoDevuelve401() throws Exception {
        when(verificadorProvider.getIfAvailable()).thenReturn(firebase);
        when(identidadProvider.getIfAvailable()).thenReturn(identidad);
        when(firebase.verificar("bad")).thenThrow(new TokenFirebaseInvalidoException());
        var res = new MockHttpServletResponse();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(solicitud("POST", "/api/v1/catalogo/publicaciones", "Bearer bad"),
                        res, (a,b) -> fail("No debe pasar"));
        assertEquals(401, res.getStatus());
        verifyNoInteractions(identidad);
    }

    @Test void correoSinVerificarDevuelve403() throws Exception {
        when(verificadorProvider.getIfAvailable()).thenReturn(firebase);
        when(identidadProvider.getIfAvailable()).thenReturn(identidad);
        when(firebase.verificar("pending"))
                .thenReturn(new FirebaseTokenVerifier.TokenVerificado("uid", false));
        var res = new MockHttpServletResponse();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(solicitud("POST", "/api/v1/catalogo/publicaciones", "Bearer pending"),
                        res, (a,b) -> fail("No debe pasar"));
        assertEquals(403, res.getStatus());
        verifyNoInteractions(identidad);
    }

    @Test void perfilNoRegistradoYServicioCaidoSeDistinguen() throws Exception {
        when(verificadorProvider.getIfAvailable()).thenReturn(firebase);
        when(identidadProvider.getIfAvailable()).thenReturn(identidad);
        when(firebase.verificar("ok")).thenReturn(new FirebaseTokenVerifier.TokenVerificado("uid", true));
        var req = solicitud("POST", "/api/v1/catalogo/publicaciones", "Bearer ok");
        when(identidad.resolverUsuario("uid", "ok")).thenThrow(new IdentidadNoRegistradaException());
        var res = new MockHttpServletResponse();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(req, res, (a,b) -> fail("No debe pasar"));
        assertEquals(403, res.getStatus());

        when(identidad.resolverUsuario("uid", "ok")).thenThrow(new IdentidadNoDisponibleException());
        var second = new MockHttpServletResponse();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(solicitud("POST", "/api/v1/catalogo/publicaciones", "Bearer ok"),
                        second, (a,b) -> fail("No debe pasar"));
        assertEquals(503, second.getStatus());
    }

    @Test void lecturaPublicaNoObligaAValidarUnToken() throws Exception {
        var res = new MockHttpServletResponse();
        var passed = new java.util.concurrent.atomic.AtomicBoolean();
        new FirebaseCatalogoAuthenticationFilter(verificadorProvider, identidadProvider)
                .doFilter(solicitud("GET", "/api/v1/catalogo/categorias", "Bearer stale"),
                        res, (a,b) -> passed.set(true));
        assertTrue(passed.get());
        verifyNoInteractions(firebase, identidad);
    }
}
