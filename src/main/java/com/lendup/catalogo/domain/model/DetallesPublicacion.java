package com.lendup.catalogo.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Datos descriptivos correspondientes al contrato de publicaciones del monolito. */
public record DetallesPublicacion(
        UUID categoriaId,
        String titulo,
        String descripcion,
        String condicionObjeto,
        String universidad,
        String campus,
        String ubicacion,
        BigDecimal latitud,
        BigDecimal longitud) {

    public DetallesPublicacion {
        categoriaId = ValidadorDominio.requerido(categoriaId, "categoriaId");
        titulo = ValidadorDominio.textoRequerido(titulo, "titulo", 180);
        descripcion = ValidadorDominio.textoRequerido(descripcion, "descripcion", Integer.MAX_VALUE);
        condicionObjeto = ValidadorDominio.textoRequerido(condicionObjeto, "condicionObjeto", Integer.MAX_VALUE);
        universidad = ValidadorDominio.textoRequerido(universidad, "universidad", 150);
        campus = ValidadorDominio.textoRequerido(campus, "campus", 150);
        ubicacion = ValidadorDominio.textoRequerido(ubicacion, "ubicacion", 250);
        if (latitud != null && (latitud.compareTo(new BigDecimal("-90")) < 0
                || latitud.compareTo(new BigDecimal("90")) > 0)) {
            throw new IllegalArgumentException("latitud fuera de rango");
        }
        if (longitud != null && (longitud.compareTo(new BigDecimal("-180")) < 0
                || longitud.compareTo(new BigDecimal("180")) > 0)) {
            throw new IllegalArgumentException("longitud fuera de rango");
        }
    }
}
