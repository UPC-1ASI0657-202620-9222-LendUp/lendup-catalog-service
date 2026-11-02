package com.lendup.catalogo.application.exception;

public final class IdentidadNoRegistradaException extends RuntimeException {
    public IdentidadNoRegistradaException() {
        super("Registra tu perfil en LendUp antes de acceder a Catálogo");
    }
}
