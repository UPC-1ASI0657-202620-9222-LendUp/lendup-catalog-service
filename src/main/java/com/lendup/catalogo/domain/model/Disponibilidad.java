package com.lendup.catalogo.domain.model;

import java.util.UUID;

/** Intervalo ofrecido por una publicación; no representa reservas confirmadas. */
public record Disponibilidad(UUID id, UUID publicacionId, PeriodoDisponibilidad periodo) {
    public Disponibilidad {
        id = ValidadorDominio.requerido(id, "id");
        publicacionId = ValidadorDominio.requerido(publicacionId, "publicacionId");
        periodo = ValidadorDominio.requerido(periodo, "periodo");
    }
}
