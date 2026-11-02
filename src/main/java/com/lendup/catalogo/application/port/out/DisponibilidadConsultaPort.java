package com.lendup.catalogo.application.port.out;

import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import java.util.List;
import java.util.UUID;

/** Las escrituras de intervalos ocurren solo a través de Publicacion. */
public interface DisponibilidadConsultaPort {
    List<Disponibilidad> listarPorPublicacion(UUID publicacionId);
    boolean periodoOfrecido(UUID publicacionId, PeriodoDisponibilidad solicitado);
}
