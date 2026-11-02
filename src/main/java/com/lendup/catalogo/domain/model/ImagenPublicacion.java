package com.lendup.catalogo.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Metadatos de imágenes del catálogo, no el archivo almacenado en Cloudinary. */
public record ImagenPublicacion(UUID id, UUID publicacionId, String cloudinaryPublicId, String url, int orden) {
    public ImagenPublicacion {
        id = Objects.requireNonNull(id, "id es obligatorio");
        publicacionId = Objects.requireNonNull(publicacionId, "publicacionId es obligatorio");
        cloudinaryPublicId = ValidadorDominio.textoRequerido(cloudinaryPublicId, "cloudinaryPublicId", 255);
        url = ValidadorDominio.textoRequerido(url, "url", Integer.MAX_VALUE);
        if (orden < 0) throw new IllegalArgumentException("orden no puede ser negativo");
    }
}
