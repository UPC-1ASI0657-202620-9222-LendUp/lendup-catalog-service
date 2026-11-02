package com.lendup.catalogo.application.port.out;

import com.lendup.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Contrato del agregado, sin dependencias de JPA. */
public interface PublicacionPersistencePort {
    Optional<Publicacion> buscarPorId(UUID id);
    /** Exige transacción activa del caso de uso; mantiene un bloqueo hasta el commit. */
    Optional<Publicacion> buscarPorIdParaActualizar(UUID id);
    /** Guarda publicación y disponibilidades dentro de la misma transacción. */
    Publicacion guardar(Publicacion publicacion);
    List<Publicacion> listarActivas();
    List<Publicacion> listarPorPropietario(UUID propietarioUsuarioId);
}
