package com.lendup.catalogo.application.service;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.ConsultarPublicacionesUseCase;
import com.lendup.catalogo.application.port.in.GestionarPublicacionesUseCase;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestación de publicaciones. Los comandos reciben UUID interno de Identidad,
 * nunca un Firebase UID ni un parámetro libre del cliente HTTP.
 */
@Service
public class PublicacionApplicationService
        implements GestionarPublicacionesUseCase, ConsultarPublicacionesUseCase {
    private final PublicacionPersistencePort publicaciones;
    private final CategoriaPersistencePort categorias;

    public PublicacionApplicationService(PublicacionPersistencePort publicaciones,
            CategoriaPersistencePort categorias) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
    }

    @Override
    @Transactional
    public Publicacion crear(Crear comando) {
        Objects.requireNonNull(comando, "comando");
        comprobarCategoria(comando.detalles().categoriaId());
        var nueva = Publicacion.nueva(UUID.randomUUID(), comando.actorUsuarioId(),
                comando.detalles(), comando.condiciones());
        return publicaciones.guardar(nueva);
    }

    @Override
    @Transactional
    public Publicacion actualizar(Actualizar comando) {
        Objects.requireNonNull(comando, "comando");
        var actual = bloquear(comando.publicacionId());
        exigirPropietario(actual, comando.actorUsuarioId());
        comprobarCategoria(comando.detalles().categoriaId());
        actual.actualizar(comando.detalles(), comando.condiciones());
        return publicaciones.guardar(actual);
    }

    @Override
    @Transactional
    public Publicacion cambiarEstado(CambiarEstado comando) {
        Objects.requireNonNull(comando, "comando");
        var actual = bloquear(comando.publicacionId());
        exigirPropietario(actual, comando.actorUsuarioId());
        actual.cambiarEstado(comando.estado());
        return publicaciones.guardar(actual);
    }

    @Override
    @Transactional(readOnly = true)
    public Publicacion obtener(UUID publicacionId) {
        return publicaciones.buscarPorId(Objects.requireNonNull(publicacionId, "publicacionId"))
                .orElseThrow(() -> new RecursoNoEncontradoException("Publicación", publicacionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Publicacion> listarActivas() {
        return publicaciones.listarActivas();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Publicacion> listarMisPublicaciones(UUID actorUsuarioId) {
        return publicaciones.listarPorPropietario(
                Objects.requireNonNull(actorUsuarioId, "actorUsuarioId"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean periodoOfrecido(UUID publicacionId, PeriodoDisponibilidad solicitado) {
        Objects.requireNonNull(solicitado, "solicitado");
        return obtener(publicacionId).ofrecePeriodo(solicitado);
    }

    private void comprobarCategoria(UUID categoriaId) {
        if (categorias.buscarPorId(categoriaId).isEmpty()) {
            throw new RecursoNoEncontradoException("Categoría", categoriaId);
        }
    }

    private Publicacion bloquear(UUID publicacionId) {
        return publicaciones.buscarPorIdParaActualizar(publicacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Publicación", publicacionId));
    }

    private void exigirPropietario(Publicacion actual, UUID actorUsuarioId) {
        if (!actual.esPropietario(actorUsuarioId)) {
            throw new AccesoDenegadoException();
        }
    }
}
