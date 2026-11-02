package com.lendup.catalogo.infrastructure.client;

import com.google.firebase.auth.FirebaseAuth;
import com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException;
import com.lendup.catalogo.application.port.out.FirebaseTokenVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "catalogo.security", name = "firebase-enabled", havingValue = "true")
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {
    private final FirebaseAuth firebase;

    public FirebaseAdminTokenVerifier(FirebaseAuth firebase) {
        this.firebase = firebase;
    }

    @Override
    public TokenVerificado verificar(String idToken) {
        if (idToken == null || idToken.isBlank()) throw new TokenFirebaseInvalidoException();
        try {
            // checkRevoked=true comprueba, además de firma y expiración, la revocación.
            var token = firebase.verifyIdToken(idToken, true);
            return new TokenVerificado(token.getUid(),
                    Boolean.TRUE.equals(token.getClaims().get("email_verified")));
        } catch (Exception ex) {
            throw new TokenFirebaseInvalidoException();
        }
    }
}
