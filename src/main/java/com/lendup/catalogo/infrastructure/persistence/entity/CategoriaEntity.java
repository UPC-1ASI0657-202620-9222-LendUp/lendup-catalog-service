package com.lendup.catalogo.infrastructure.persistence.entity;

import com.lendup.catalogo.domain.model.Categoria;
import jakarta.persistence.*;

@Entity
@Table(name = "categorias")
public class CategoriaEntity {
    @Id @Column(name = "id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "codigo", nullable = false, length = 50, unique = true)
    private String codigo;
    @Column(name = "nombre", nullable = false, length = 100, unique = true)
    private String nombre;
    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected CategoriaEntity() {}
    public CategoriaEntity(Categoria categoria) {
        this.id = categoria.id().toString();
        actualizarDesde(categoria);
    }
    public void actualizarDesde(Categoria categoria) {
        if (id == null || !id.equals(categoria.id().toString()))
            throw new IllegalArgumentException("No puede cambiarse el id de la categoría");
        codigo = categoria.codigo();
        nombre = categoria.nombre();
        activa = categoria.activa();
    }
    public Categoria aDominio() {
        return new Categoria(java.util.UUID.fromString(id), codigo, nombre, activa);
    }
}
