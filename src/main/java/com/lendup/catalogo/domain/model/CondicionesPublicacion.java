package com.lendup.catalogo.domain.model;

import java.math.BigDecimal;

/**
 * Condiciones que Reservas necesitará copiar de forma inmutable
 * al snapshot acordado de una solicitud.
 */
public record CondicionesPublicacion(
        BigDecimal tarifaDiaria,
        BigDecimal garantiaMonetaria,
        String moneda,
        String lugarIntercambio,
        String condicionesUso,
        String condicionesEntrega,
        String condicionesDevolucion,
        String condicionesCancelacion) {

    public CondicionesPublicacion {
        tarifaDiaria = ValidadorDominio.requerido(tarifaDiaria, "tarifaDiaria");
        if (tarifaDiaria.signum() < 0) {
            throw new IllegalArgumentException("tarifaDiaria no puede ser negativa");
        }
        if (garantiaMonetaria != null && garantiaMonetaria.signum() < 0) {
            throw new IllegalArgumentException("garantiaMonetaria no puede ser negativa");
        }
        moneda = ValidadorDominio.textoRequerido(moneda, "moneda", 3);
        if (!moneda.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("moneda debe ser un código de tres letras mayúsculas");
        }
        lugarIntercambio = ValidadorDominio.textoRequerido(lugarIntercambio, "lugarIntercambio", 250);
        condicionesUso = ValidadorDominio.textoRequerido(condicionesUso, "condicionesUso", Integer.MAX_VALUE);
        condicionesEntrega = ValidadorDominio.textoRequerido(condicionesEntrega, "condicionesEntrega", Integer.MAX_VALUE);
        condicionesDevolucion = ValidadorDominio.textoRequerido(condicionesDevolucion, "condicionesDevolucion", Integer.MAX_VALUE);
        condicionesCancelacion = ValidadorDominio.textoRequerido(condicionesCancelacion, "condicionesCancelacion", Integer.MAX_VALUE);
    }
}
