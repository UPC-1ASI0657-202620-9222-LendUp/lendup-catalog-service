package com.lendup.catalogo.domain.model;

/** Intervalo solapado con otro ofrecido; la API REST lo representa como HTTP 409 (Conflict). */
public final class DisponibilidadSuperpuestaException extends IllegalArgumentException {
    public DisponibilidadSuperpuestaException() {
        super("El intervalo se superpone con otra disponibilidad");
    }
}
