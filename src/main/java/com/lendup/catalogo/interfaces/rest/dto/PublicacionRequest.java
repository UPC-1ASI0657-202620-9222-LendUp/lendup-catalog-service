package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.Publicacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PublicacionRequest(
        @NotNull @Valid DetallesRequest detalles,
        @NotNull @Valid CondicionesRequest condiciones) {
    public static PublicacionRequest deDominio(Publicacion publicacion) {
        return new PublicacionRequest(DetallesRequest.deDominio(publicacion.detalles()),
                CondicionesRequest.deDominio(publicacion.condiciones()));
    }
}
