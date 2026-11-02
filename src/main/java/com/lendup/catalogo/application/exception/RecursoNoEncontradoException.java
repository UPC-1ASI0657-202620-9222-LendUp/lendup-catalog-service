package com.lendup.catalogo.application.exception;

import java.util.Objects;
import java.util.UUID;

/** Excepción de caso de uso; la capa HTTP decidirá su respuesta (404). */
public final class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String recurso, UUID id) {
        super(Objects.requireNonNull(recurso, "recurso") + " no encontrado: "
                + Objects.requireNonNull(id, "id"));
    }
}
