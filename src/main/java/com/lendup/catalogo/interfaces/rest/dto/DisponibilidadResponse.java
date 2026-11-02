package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.Disponibilidad;
import java.time.LocalDateTime;
import java.util.UUID;

public record DisponibilidadResponse(UUID id, UUID publicacionId,
        LocalDateTime desde, LocalDateTime hasta) {
    public static DisponibilidadResponse deDominio(Disponibilidad disponibilidad) {
        return new DisponibilidadResponse(disponibilidad.id(), disponibilidad.publicacionId(),
                disponibilidad.periodo().desde(), disponibilidad.periodo().hasta());
    }
}
