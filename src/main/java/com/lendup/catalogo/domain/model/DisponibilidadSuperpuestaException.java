package com.lendup.catalogo.domain.model;

/** Intervalo solapado con otro ofrecido, candidato a HTTP 409 en el paso REST. */
public final class DisponibilidadSuperpuestaException extends IllegalArgumentException {
    public DisponibilidadSuperpuestaException() {
        super("El intervalo se superpone con otra disponibilidad");
    }
}
