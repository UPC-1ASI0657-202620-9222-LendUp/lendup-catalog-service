package com.lendup.catalogo.domain.model;

import java.util.UUID;

/** Categoría de publicaciones; activa controla su presencia en búsquedas públicas. */
public record Categoria(UUID id, String codigo, String nombre, boolean activa) {
    public Categoria {
        id = ValidadorDominio.requerido(id, "id");
        codigo = ValidadorDominio.textoRequerido(codigo, "codigo", 50);
        nombre = ValidadorDominio.textoRequerido(nombre, "nombre", 100);
    }
}
