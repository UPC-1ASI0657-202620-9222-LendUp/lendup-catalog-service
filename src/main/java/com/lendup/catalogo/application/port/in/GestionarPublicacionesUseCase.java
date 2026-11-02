package com.lendup.catalogo.application.port.in;

import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.Publicacion;
import java.util.Objects;
import java.util.UUID;

/** Entradas de escritura; el UUID del actor lo obtendrá REST de una identidad autenticada. */
public interface GestionarPublicacionesUseCase {
    record Crear(UUID actorUsuarioId, DetallesPublicacion detalles, CondicionesPublicacion condiciones) {
        public Crear {
            Objects.requireNonNull(actorUsuarioId, "actorUsuarioId");
            Objects.requireNonNull(detalles, "detalles");
            Objects.requireNonNull(condiciones, "condiciones");
        }
    }

    /** Reemplazo completo del modelo; el adaptador REST fusionará los PATCH parciales del monolito. */
    record Actualizar(UUID publicacionId, UUID actorUsuarioId,
            DetallesPublicacion detalles, CondicionesPublicacion condiciones) {
        public Actualizar {
            Objects.requireNonNull(publicacionId, "publicacionId");
            Objects.requireNonNull(actorUsuarioId, "actorUsuarioId");
            Objects.requireNonNull(detalles, "detalles");
            Objects.requireNonNull(condiciones, "condiciones");
        }
    }

    record CambiarEstado(UUID publicacionId, UUID actorUsuarioId, EstadoPublicacion estado) {
        public CambiarEstado {
            Objects.requireNonNull(publicacionId, "publicacionId");
            Objects.requireNonNull(actorUsuarioId, "actorUsuarioId");
            Objects.requireNonNull(estado, "estado");
        }
    }

    Publicacion crear(Crear comando);
    Publicacion actualizar(Actualizar comando);
    Publicacion cambiarEstado(CambiarEstado comando);
}
