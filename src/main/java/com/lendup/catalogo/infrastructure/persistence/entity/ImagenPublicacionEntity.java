package com.lendup.catalogo.infrastructure.persistence.entity;

import com.lendup.catalogo.domain.model.ImagenPublicacion;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name="imagenes_publicacion")
public class ImagenPublicacionEntity {
    @Id @Column(name="id", nullable=false, length=36, columnDefinition="char(36)")
    private String id;
    @Column(name="publicacion_id", nullable=false, length=36, columnDefinition="char(36)")
    private String publicacionId;
    @Column(name="cloudinary_public_id", nullable=false, length=255)
    private String cloudinaryPublicId;
    @Column(name="url", nullable=false, columnDefinition="text")
    private String url;
    @Column(name="orden", nullable=false)
    private int orden;
    @Column(name="creado_en", nullable=false, updatable=false, columnDefinition="datetime(6)")
    private LocalDateTime creadoEn;

    protected ImagenPublicacionEntity() {}
    public ImagenPublicacionEntity(ImagenPublicacion i) {
        id=i.id().toString(); publicacionId=i.publicacionId().toString(); actualizarDesde(i);
    }
    public String getPublicacionId() { return publicacionId; }
    public void actualizarDesde(ImagenPublicacion i) {
        if (!id.equals(i.id().toString()) || !publicacionId.equals(i.publicacionId().toString()))
            throw new IllegalArgumentException("No puede cambiarse la identidad de la imagen");
        cloudinaryPublicId=i.cloudinaryPublicId(); url=i.url(); orden=i.orden();
    }
    public ImagenPublicacion aDominio() {
        return new ImagenPublicacion(UUID.fromString(id), UUID.fromString(publicacionId),
                cloudinaryPublicId, url, orden);
    }
    @PrePersist void antesDeInsertar() { creadoEn=LocalDateTime.now(ZoneOffset.UTC); }
}
