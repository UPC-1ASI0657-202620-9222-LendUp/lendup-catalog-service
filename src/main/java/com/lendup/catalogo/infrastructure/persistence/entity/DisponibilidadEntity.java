package com.lendup.catalogo.infrastructure.persistence.entity;

import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name="disponibilidades_publicacion")
public class DisponibilidadEntity {
    @Id @Column(name="id", nullable=false, length=36, columnDefinition="char(36)")
    private String id;
    @Column(name="publicacion_id", nullable=false, length=36, columnDefinition="char(36)")
    private String publicacionId;
    @Column(name="desde", nullable=false, columnDefinition="datetime(6)")
    private LocalDateTime desde;
    @Column(name="hasta", nullable=false, columnDefinition="datetime(6)")
    private LocalDateTime hasta;
    @Column(name="creado_en", nullable=false, updatable=false, columnDefinition="datetime(6)")
    private LocalDateTime creadoEn;

    protected DisponibilidadEntity() {}
    public DisponibilidadEntity(Disponibilidad d) {
        id=d.id().toString();
        publicacionId=d.publicacionId().toString();
        actualizarPeriodo(d);
    }
    public String getId() { return id; }
    public String getPublicacionId() { return publicacionId; }
    public void actualizarPeriodo(Disponibilidad d) {
        if (!id.equals(d.id().toString()) || !publicacionId.equals(d.publicacionId().toString()))
            throw new IllegalArgumentException("No puede cambiarse la identidad de la disponibilidad");
        desde=d.periodo().desde();
        hasta=d.periodo().hasta();
    }
    public Disponibilidad aDominio() {
        return new Disponibilidad(UUID.fromString(id), UUID.fromString(publicacionId),
                new PeriodoDisponibilidad(desde, hasta));
    }
    @PrePersist void antesDeInsertar() { creadoEn=LocalDateTime.now(ZoneOffset.UTC); }
}
