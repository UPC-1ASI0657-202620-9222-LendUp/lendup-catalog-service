package com.lendup.catalogo.infrastructure.persistence.entity;

import com.lendup.catalogo.domain.model.DetallesPublicacion;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class DetallesPublicacionEmbeddable {
    @Column(name="categoria_id", nullable=false, length=36, columnDefinition="char(36)")
    private String categoriaId;
    @Column(name="titulo", nullable=false, length=180)
    private String titulo;
    @Column(name="descripcion", nullable=false, columnDefinition="text")
    private String descripcion;
    @Column(name="condicion_objeto", nullable=false, columnDefinition="text")
    private String condicionObjeto;
    @Column(name="universidad", nullable=false, length=150)
    private String universidad;
    @Column(name="campus", nullable=false, length=150)
    private String campus;
    @Column(name="ubicacion", nullable=false, length=250)
    private String ubicacion;
    @Column(name="latitud", precision=9, scale=6)
    private BigDecimal latitud;
    @Column(name="longitud", precision=9, scale=6)
    private BigDecimal longitud;

    protected DetallesPublicacionEmbeddable() {}
    public DetallesPublicacionEmbeddable(DetallesPublicacion d) {
        categoriaId=d.categoriaId().toString(); titulo=d.titulo(); descripcion=d.descripcion();
        condicionObjeto=d.condicionObjeto(); universidad=d.universidad(); campus=d.campus();
        ubicacion=d.ubicacion(); latitud=d.latitud(); longitud=d.longitud();
    }
    public DetallesPublicacion aDominio() {
        return new DetallesPublicacion(UUID.fromString(categoriaId), titulo, descripcion,
                condicionObjeto, universidad, campus, ubicacion, latitud, longitud);
    }
}
