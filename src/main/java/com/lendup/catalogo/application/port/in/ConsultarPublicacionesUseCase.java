package com.lendup.catalogo.application.port.in;

import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.UUID;

/** Consultas de Catálogo; periodoOfrecido NO comprueba reservas confirmadas. */
public interface ConsultarPublicacionesUseCase {
    Publicacion obtener(UUID publicacionId);
    List<Publicacion> listarActivas();
    List<Publicacion> listarMisPublicaciones(UUID actorUsuarioId);
    boolean periodoOfrecido(UUID publicacionId, PeriodoDisponibilidad solicitado);
}
