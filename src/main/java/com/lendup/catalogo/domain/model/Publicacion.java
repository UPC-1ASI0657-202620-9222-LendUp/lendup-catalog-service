package com.lendup.catalogo.domain.model;

import com.lendup.catalogo.domain.specification.DisponibilidadSpecification;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de Catálogo. El propietario es un UUID de Identidad, no una relación JPA
 * hacia usuarios ni un UID Firebase. La autorización de la solicitud entrante se
 * resolverá en aplicación, antes de llamar a los métodos de modificación.
 */
public final class Publicacion {
    private final UUID id;
    private final UUID propietarioUsuarioId;
    private DetallesPublicacion detalles;
    private CondicionesPublicacion condiciones;
    private EstadoPublicacion estado;
    private final Map<UUID, Disponibilidad> disponibilidades = new LinkedHashMap<>();

    public Publicacion(UUID id, UUID propietarioUsuarioId, DetallesPublicacion detalles,
            CondicionesPublicacion condiciones, EstadoPublicacion estado,
            Collection<Disponibilidad> disponibilidadesIniciales) {
        this.id = ValidadorDominio.requerido(id, "id");
        this.propietarioUsuarioId = ValidadorDominio.requerido(propietarioUsuarioId, "propietarioUsuarioId");
        this.detalles = ValidadorDominio.requerido(detalles, "detalles");
        this.condiciones = ValidadorDominio.requerido(condiciones, "condiciones");
        this.estado = ValidadorDominio.requerido(estado, "estado");
        ValidadorDominio.requerido(disponibilidadesIniciales, "disponibilidadesIniciales")
                .forEach(this::agregarDisponibilidad);
    }

    public static Publicacion nueva(UUID id, UUID propietarioUsuarioId, DetallesPublicacion detalles,
            CondicionesPublicacion condiciones) {
        return new Publicacion(id, propietarioUsuarioId, detalles, condiciones,
                EstadoPublicacion.ACTIVA, List.of());
    }

    public UUID id() {
        return id;
    }

    public UUID propietarioUsuarioId() {
        return propietarioUsuarioId;
    }

    public DetallesPublicacion detalles() {
        return detalles;
    }

    public CondicionesPublicacion condiciones() {
        return condiciones;
    }

    public EstadoPublicacion estado() {
        return estado;
    }

    public boolean esPropietario(UUID usuarioId) {
        return propietarioUsuarioId.equals(Objects.requireNonNull(usuarioId, "usuarioId"));
    }

    public void actualizar(DetallesPublicacion nuevosDetalles, CondicionesPublicacion nuevasCondiciones) {
        ValidadorDominio.requerido(nuevosDetalles, "nuevosDetalles");
        ValidadorDominio.requerido(nuevasCondiciones, "nuevasCondiciones");
        detalles = nuevosDetalles;
        condiciones = nuevasCondiciones;
    }

    /**
     * Mantiene los tres estados del monolito. No introduce restricciones entre
     * transiciones que el comportamiento original no definió.
     */
    public void cambiarEstado(EstadoPublicacion nuevoEstado) {
        estado = ValidadorDominio.requerido(nuevoEstado, "nuevoEstado");
    }

    public List<Disponibilidad> disponibilidades() {
        return List.copyOf(disponibilidades.values());
    }

    public void agregarDisponibilidad(Disponibilidad disponibilidad) {
        verificarPublicacion(disponibilidad);
        if (disponibilidades.containsKey(disponibilidad.id())) {
            throw new IllegalArgumentException("Ya existe un intervalo con ese id");
        }
        validarSinSolapamiento(disponibilidad, null);
        disponibilidades.put(disponibilidad.id(), disponibilidad);
    }

    public void actualizarDisponibilidad(Disponibilidad disponibilidad) {
        verificarPublicacion(disponibilidad);
        if (!disponibilidades.containsKey(disponibilidad.id())) {
            throw new NoSuchElementException("Intervalo de disponibilidad no encontrado");
        }
        validarSinSolapamiento(disponibilidad, disponibilidad.id());
        disponibilidades.put(disponibilidad.id(), disponibilidad);
    }

    public void eliminarDisponibilidad(UUID disponibilidadId) {
        Objects.requireNonNull(disponibilidadId, "disponibilidadId");
        if (disponibilidades.remove(disponibilidadId) == null) {
            throw new NoSuchElementException("Intervalo de disponibilidad no encontrado");
        }
    }

    /** Disponibilidad ofrecida, independiente de posibles bloqueos de Reservas. */
    public boolean ofrecePeriodo(PeriodoDisponibilidad solicitado) {
        return DisponibilidadSpecification.periodoOfrecido(disponibilidades.values(), solicitado);
    }

    private void verificarPublicacion(Disponibilidad disponibilidad) {
        Objects.requireNonNull(disponibilidad, "disponibilidad");
        if (!id.equals(disponibilidad.publicacionId())) {
            throw new IllegalArgumentException("La disponibilidad pertenece a otra publicación");
        }
    }

    private void validarSinSolapamiento(Disponibilidad candidata, UUID idExcluido) {
        if (!DisponibilidadSpecification.sinSolapamiento(
                disponibilidades.values(), candidata.periodo(), idExcluido)) {
            throw new IllegalArgumentException("El intervalo se superpone con otra disponibilidad");
        }
    }
}
