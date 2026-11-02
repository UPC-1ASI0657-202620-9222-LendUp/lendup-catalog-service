package com.lendup.catalogo.infrastructure.persistence.entity;

import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.Publicacion;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.UUID;

/** Sin relación JPA hacia usuarios de Identidad. */
@Entity
@Table(name="publicaciones")
public class PublicacionEntity {
    @Id @Column(name="id", nullable=false, length=36, columnDefinition="char(36)")
    private String id;
    @Column(name="propietario_usuario_id", nullable=false, length=36, columnDefinition="char(36)")
    private String propietarioUsuarioId;
    @Embedded
    private DetallesPublicacionEmbeddable detalles;
    @Embedded
    private CondicionesPublicacionEmbeddable condiciones;
    // String: V1 tiene VARCHAR(20) (no enum nativo MySQL).
    @Column(name="estado", nullable=false, length=20)
    private String estado;
    @Column(name="creado_en", nullable=false, columnDefinition="datetime(6)")
    private LocalDateTime creadoEn;
    @Column(name="actualizado_en", nullable=false, columnDefinition="datetime(6)")
    private LocalDateTime actualizadoEn;

    protected PublicacionEntity() {}
    public PublicacionEntity(Publicacion publicacion) {
        id=publicacion.id().toString();
        propietarioUsuarioId=publicacion.propietarioUsuarioId().toString();
        actualizarDesde(publicacion);
    }
    public String getId() { return id; }
    public String getPropietarioUsuarioId() { return propietarioUsuarioId; }
    public void actualizarDesde(Publicacion p) {
        if (!id.equals(p.id().toString()) || !propietarioUsuarioId.equals(p.propietarioUsuarioId().toString()))
            throw new IllegalArgumentException("No puede cambiarse la identidad ni el propietario");
        detalles=new DetallesPublicacionEmbeddable(p.detalles());
        condiciones=new CondicionesPublicacionEmbeddable(p.condiciones());
        estado=p.estado().name();
    }
    public Publicacion aDominio(Collection<Disponibilidad> disponibilidades) {
        return new Publicacion(UUID.fromString(id), UUID.fromString(propietarioUsuarioId),
                detalles.aDominio(), condiciones.aDominio(), EstadoPublicacion.valueOf(estado), disponibilidades);
    }
    @PrePersist void antesDeInsertar() {
        creadoEn=LocalDateTime.now(ZoneOffset.UTC);
        actualizadoEn=creadoEn;
    }
    @PreUpdate void antesDeActualizar() {
        actualizadoEn=LocalDateTime.now(ZoneOffset.UTC);
    }
}
