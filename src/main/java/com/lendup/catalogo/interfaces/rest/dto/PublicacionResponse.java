package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.UUID;

public record PublicacionResponse(UUID id, UUID propietarioUsuarioId,
        DetallesPublicacion detalles, CondicionesPublicacion condiciones,
        EstadoPublicacion estado, List<DisponibilidadResponse> disponibilidades) {
    public static PublicacionResponse deDominio(Publicacion publicacion) {
        return new PublicacionResponse(publicacion.id(), publicacion.propietarioUsuarioId(),
                publicacion.detalles(), publicacion.condiciones(), publicacion.estado(),
                publicacion.disponibilidades().stream().map(DisponibilidadResponse::deDominio).toList());
    }
}
