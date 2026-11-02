package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.Categoria;
import java.util.UUID;

public record CategoriaResponse(UUID id, String codigo, String nombre, boolean activa) {
    public static CategoriaResponse deDominio(Categoria categoria) {
        return new CategoriaResponse(categoria.id(), categoria.codigo(),
                categoria.nombre(), categoria.activa());
    }
}
