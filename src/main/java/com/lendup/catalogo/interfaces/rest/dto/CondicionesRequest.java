package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CondicionesRequest(
        @NotNull @DecimalMin("0") BigDecimal tarifaDiaria,
        @DecimalMin("0") BigDecimal garantiaMonetaria,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String moneda,
        @NotBlank @Size(max = 250) String lugarIntercambio,
        @NotBlank String condicionesUso,
        @NotBlank String condicionesEntrega,
        @NotBlank String condicionesDevolucion,
        @NotBlank String condicionesCancelacion) {
    public CondicionesPublicacion aDominio() {
        return new CondicionesPublicacion(tarifaDiaria, garantiaMonetaria, moneda,
                lugarIntercambio, condicionesUso, condicionesEntrega,
                condicionesDevolucion, condicionesCancelacion);
    }

    public static CondicionesRequest deDominio(CondicionesPublicacion condiciones) {
        return new CondicionesRequest(condiciones.tarifaDiaria(), condiciones.garantiaMonetaria(),
                condiciones.moneda(), condiciones.lugarIntercambio(),
                condiciones.condicionesUso(), condiciones.condicionesEntrega(),
                condiciones.condicionesDevolucion(), condiciones.condicionesCancelacion());
    }
}
