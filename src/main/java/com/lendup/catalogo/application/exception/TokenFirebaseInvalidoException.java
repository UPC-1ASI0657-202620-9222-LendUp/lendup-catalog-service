package com.lendup.catalogo.application.exception;

public final class TokenFirebaseInvalidoException extends RuntimeException {
    public TokenFirebaseInvalidoException() {
        super("Token Firebase no válido");
    }
}
