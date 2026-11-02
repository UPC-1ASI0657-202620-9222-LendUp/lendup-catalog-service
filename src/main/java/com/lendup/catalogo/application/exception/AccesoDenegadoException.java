package com.lendup.catalogo.application.exception;

/** El actor no posee la publicación; se mapeará a un 403 en REST. */
public final class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException() {
        super("Solo el propietario puede realizar esta operación");
    }
}
