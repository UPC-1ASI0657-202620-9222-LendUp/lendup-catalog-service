package com.lendup.catalogo.application.port.in;

import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Operaciones del propietario sobre periodos ofrecidos, no sobre reservas. */
public interface GestionarDisponibilidadUseCase {
    record Agregar(UUID publicacionId, UUID actorUsuarioId, PeriodoDisponibilidad periodo) {
        public Agregar {
            Objects.requireNonNull(publicacionId, "publicacionId");
            Objects.requireNonNull(actorUsuarioId, "actorUsuarioId");
            Objects.requireNonNull(periodo, "periodo");
        }
    }

    record Actualizar(UUID publicacionId, UUID disponibilidadId,
            UUID actorUsuarioId, PeriodoDisponibilidad periodo) {
        public Actualizar {
            Objects.requireNonNull(publicacionId, "publicacionId");
            Objects.requireNonNull(disponibilidadId, "disponibilidadId");
            Objects.requireNonNull(actorUsuarioId, "actorUsuarioId");
            Objects.requireNonNull(periodo, "periodo");
        }
    }

    record Eliminar(UUID publicacionId, UUID disponibilidadId, UUID actorUsuarioId) {
        public Eliminar {
            Objects.requireNonNull(publicacionId, "publicacionId");
            Objects.requireNonNull(disponibilidadId, "disponibilidadId");
            Objects.requireNonNull(actorUsuarioId, "actorUsuarioId");
        }
    }

    Disponibilidad agregar(Agregar comando);
    Disponibilidad actualizar(Actualizar comando);
    void eliminar(Eliminar comando);
    List<Disponibilidad> listar(UUID publicacionId, UUID actorUsuarioId);
}
