package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record DisponibilidadRequest(@NotNull LocalDateTime desde,
        @NotNull LocalDateTime hasta) {
    public PeriodoDisponibilidad aDominio() {
        return new PeriodoDisponibilidad(desde, hasta);
    }
}
