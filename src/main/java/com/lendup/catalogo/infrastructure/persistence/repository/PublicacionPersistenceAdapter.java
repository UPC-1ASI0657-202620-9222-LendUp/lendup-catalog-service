package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import com.lendup.catalogo.infrastructure.persistence.entity.DisponibilidadEntity;
import com.lendup.catalogo.infrastructure.persistence.entity.PublicacionEntity;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * El bloqueo se hace sobre publicaciones, no sobre tablas de Reservas.
 * El caso de uso debe leer con bloqueo y escribir dentro de LA MISMA transacción
 * para evitar que dos actualizaciones concurrentes reemplacen disponibilidades.
 */
@Repository
public class PublicacionPersistenceAdapter implements PublicacionPersistencePort {
    private final PublicacionJpaRepository publicaciones;
    private final DisponibilidadJpaRepository disponibilidades;

    public PublicacionPersistenceAdapter(PublicacionJpaRepository publicaciones,
            DisponibilidadJpaRepository disponibilidades) {
        this.publicaciones = publicaciones;
        this.disponibilidades = disponibilidades;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Publicacion> buscarPorId(UUID id) {
        return publicaciones.findById(Objects.requireNonNull(id, "id").toString())
                .map(this::reconstruir);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Publicacion> buscarPorIdParaActualizar(UUID id) {
        return publicaciones.buscarPorIdConBloqueo(Objects.requireNonNull(id, "id").toString())
                .map(this::reconstruir);
    }

    @Override
    @Transactional
    public Publicacion guardar(Publicacion publicacion) {
        Objects.requireNonNull(publicacion, "publicacion");
        var existente = publicaciones.buscarPorIdConBloqueo(publicacion.id().toString());
        PublicacionEntity entidad;
        if (existente.isPresent()) {
            entidad = existente.get();
            entidad.actualizarDesde(publicacion); // prohíbe modificar el propietario o id
        } else {
            entidad = new PublicacionEntity(publicacion);
        }
        // El INSERT/UPDATE del padre ocurre antes de insertar intervalos (FK).
        publicaciones.saveAndFlush(entidad);
        sincronizarDisponibilidades(publicacion);
        return publicacion;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Publicacion> listarActivas() {
        return publicaciones.buscarPublicasActivas().stream().map(this::reconstruir).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Publicacion> listarPorPropietario(UUID propietarioUsuarioId) {
        return publicaciones.findByPropietarioUsuarioIdOrderByCreadoEnDescIdDesc(
                Objects.requireNonNull(propietarioUsuarioId, "propietarioUsuarioId").toString())
                .stream().map(this::reconstruir).toList();
    }

    private Publicacion reconstruir(PublicacionEntity entidad) {
        List<Disponibilidad> intervalos = disponibilidades.findByPublicacionIdOrderByDesdeAscIdAsc(entidad.getId())
                .stream().map(DisponibilidadEntity::aDominio).toList();
        return entidad.aDominio(intervalos);
    }

    private void sincronizarDisponibilidades(Publicacion publicacion) {
        var guardadas = disponibilidades.findByPublicacionIdOrderByDesdeAscIdAsc(publicacion.id().toString());
        Map<String, DisponibilidadEntity> porId = new HashMap<>();
        for (var entidad : guardadas) porId.put(entidad.getId(), entidad);

        Set<String> deseadas = new HashSet<>();
        for (Disponibilidad intervalo : publicacion.disponibilidades()) {
            String id = intervalo.id().toString();
            deseadas.add(id);
            var guardada = porId.get(id);
            if (guardada == null) {
                // Si el ID ya pertenecía a otra publicación, la PK rechazará la inserción.
                disponibilidades.save(new DisponibilidadEntity(intervalo));
            } else {
                guardada.actualizarPeriodo(intervalo);
            }
        }
        for (var anterior : guardadas) {
            if (!deseadas.contains(anterior.getId())) disponibilidades.delete(anterior);
        }
        // Se comprueban errores en la misma transacción; si falla, se revierte todo.
        disponibilidades.flush();
    }
}
