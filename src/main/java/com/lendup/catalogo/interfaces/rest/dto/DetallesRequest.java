package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.DetallesPublicacion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record DetallesRequest(
        @NotNull UUID categoriaId,
        @NotBlank @Size(max = 180) String titulo,
        @NotBlank String descripcion,
        @NotBlank String condicionObjeto,
        @NotBlank @Size(max = 150) String universidad,
        @NotBlank @Size(max = 150) String campus,
        @NotBlank @Size(max = 250) String ubicacion,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud) {
    public DetallesPublicacion aDominio() {
        return new DetallesPublicacion(categoriaId, titulo, descripcion, condicionObjeto,
                universidad, campus, ubicacion, latitud, longitud);
    }

    public static DetallesRequest deDominio(DetallesPublicacion detalles) {
        return new DetallesRequest(detalles.categoriaId(), detalles.titulo(), detalles.descripcion(),
                detalles.condicionObjeto(), detalles.universidad(), detalles.campus(),
                detalles.ubicacion(), detalles.latitud(), detalles.longitud());
    }
}
