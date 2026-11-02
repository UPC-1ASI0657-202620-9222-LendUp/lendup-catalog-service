package com.lendup.catalogo.application.exception;

public final class IdentidadNoDisponibleException extends RuntimeException {
    public IdentidadNoDisponibleException() {
        super("No se pudo consultar Identidad");
    }
}
