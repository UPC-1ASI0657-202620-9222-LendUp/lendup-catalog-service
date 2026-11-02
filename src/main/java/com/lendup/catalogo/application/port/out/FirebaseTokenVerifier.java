package com.lendup.catalogo.application.port.out;

/** Verifica criptográficamente ID tokens Firebase, sin confiar en datos del cliente. */
public interface FirebaseTokenVerifier {
    record TokenVerificado(String uid, boolean correoVerificado) {
        public TokenVerificado {
            if (uid == null || uid.isBlank()) throw new IllegalArgumentException("UID inválido");
        }
    }

    TokenVerificado verificar(String idToken);
}
