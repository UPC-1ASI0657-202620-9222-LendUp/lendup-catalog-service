package com.lendup.catalogo.infrastructure.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebaseAdminTokenVerifierTest {
    @Test void verificaRevocacionYCorreoVerificado() throws Exception {
        var auth = mock(FirebaseAuth.class);
        var token = mock(FirebaseToken.class);
        when(auth.verifyIdToken("real", true)).thenReturn(token);
        when(token.getUid()).thenReturn("firebase-uid");
        when(token.getClaims()).thenReturn(Map.of("email_verified", true));
        var resultado = new FirebaseAdminTokenVerifier(auth).verificar("real");
        assertEquals("firebase-uid", resultado.uid());
        assertTrue(resultado.correoVerificado());
        verify(auth).verifyIdToken("real", true);
    }

    @Test void ausenciaDeClaimNoEquivaleACorreoVerificado() throws Exception {
        var auth = mock(FirebaseAuth.class);
        var token = mock(FirebaseToken.class);
        when(auth.verifyIdToken("real", true)).thenReturn(token);
        when(token.getUid()).thenReturn("firebase-uid");
        when(token.getClaims()).thenReturn(Map.of());
        assertFalse(new FirebaseAdminTokenVerifier(auth).verificar("real").correoVerificado());
    }

    @Test void tokenVacioSeRechazaAntesDeConsultarFirebase() {
        var auth = mock(FirebaseAuth.class);
        assertThrows(TokenFirebaseInvalidoException.class,
                () -> new FirebaseAdminTokenVerifier(auth).verificar(" "));
        verifyNoInteractions(auth);
    }
}
