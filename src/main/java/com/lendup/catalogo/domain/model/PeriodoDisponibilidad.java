package com.lendup.catalogo.domain.model;

import java.time.LocalDateTime;

/**
 * Intervalo semiabierto [desde, hasta) con fechas locales de negocio.
 * La conversión desde/hacia UTC corresponde a la capa de interfaces.
 */
public record PeriodoDisponibilidad(LocalDateTime desde, LocalDateTime hasta) {
    public PeriodoDisponibilidad {
        desde = ValidadorDominio.requerido(desde, "desde");
        hasta = ValidadorDominio.requerido(hasta, "hasta");
        if (!desde.isBefore(hasta)) {
            throw new IllegalArgumentException("desde debe ser anterior a hasta");
        }
    }

    public boolean seSuperpone(PeriodoDisponibilidad otro) {
        ValidadorDominio.requerido(otro, "otro");
        return desde.isBefore(otro.hasta) && hasta.isAfter(otro.desde);
    }

    public boolean contiene(PeriodoDisponibilidad otro) {
        ValidadorDominio.requerido(otro, "otro");
        return !desde.isAfter(otro.desde) && !hasta.isBefore(otro.hasta);
    }
}
