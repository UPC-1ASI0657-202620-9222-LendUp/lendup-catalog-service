package com.lendup.catalogo.application.service;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.GestionarDisponibilidadUseCase;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueo, autorización, validación de intervalos y guardado comparten transacción.
 * La comprobación de reservas/ocupación es responsabilidad de Reservas.
 */
@Service
public class DisponibilidadApplicationService implements GestionarDisponibilidadUseCase {
    private final PublicacionPersistencePort publicaciones;

    public DisponibilidadApplicationService(PublicacionPersistencePort publicaciones) {
        this.publicaciones = publicaciones;
    }

    @Override
    @Transactional
    public Disponibilidad agregar(Agregar comando) {
        Objects.requireNonNull(comando, "comando");
        var actual = bloquearYAutorizar(comando.publicacionId(), comando.actorUsuarioId());
        var nueva = new Disponibilidad(UUID.randomUUID(), actual.id(), comando.periodo());
        actual.agregarDisponibilidad(nueva);
        publicaciones.guardar(actual);
        return nueva;
    }

    @Override
    @Transactional
    public Disponibilidad actualizar(Actualizar comando) {
        Objects.requireNonNull(comando, "comando");
        var actual = bloquearYAutorizar(comando.publicacionId(), comando.actorUsuarioId());
        var cambio = new Disponibilidad(comando.disponibilidadId(), actual.id(), comando.periodo());
        try {
            actual.actualizarDisponibilidad(cambio);
        } catch (NoSuchElementException ex) {
            throw new RecursoNoEncontradoException("Disponibilidad", comando.disponibilidadId());
        }
        publicaciones.guardar(actual);
        return cambio;
    }

    @Override
    @Transactional
    public void eliminar(Eliminar comando) {
        Objects.requireNonNull(comando, "comando");
        var actual = bloquearYAutorizar(comando.publicacionId(), comando.actorUsuarioId());
        try {
            actual.eliminarDisponibilidad(comando.disponibilidadId());
        } catch (NoSuchElementException ex) {
            throw new RecursoNoEncontradoException("Disponibilidad", comando.disponibilidadId());
        }
        publicaciones.guardar(actual);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Disponibilidad> listar(UUID publicacionId, UUID actorUsuarioId) {
        var actual = publicaciones.buscarPorId(Objects.requireNonNull(publicacionId, "publicacionId"))
                .orElseThrow(() -> new RecursoNoEncontradoException("Publicación", publicacionId));
        exigirPropietario(actual, Objects.requireNonNull(actorUsuarioId, "actorUsuarioId"));
        return actual.disponibilidades();
    }

    private Publicacion bloquearYAutorizar(UUID publicacionId, UUID actorUsuarioId) {
        var actual = publicaciones.buscarPorIdParaActualizar(publicacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Publicación", publicacionId));
        exigirPropietario(actual, actorUsuarioId);
        return actual;
    }

    private void exigirPropietario(Publicacion actual, UUID actorUsuarioId) {
        if (!actual.esPropietario(actorUsuarioId)) {
            throw new AccesoDenegadoException();
        }
    }
}
