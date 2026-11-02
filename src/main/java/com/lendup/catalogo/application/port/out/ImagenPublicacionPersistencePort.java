package com.lendup.catalogo.application.port.out;

import com.lendup.catalogo.domain.model.ImagenPublicacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Solo metadatos; no realiza operaciones en Cloudinary. */
public interface ImagenPublicacionPersistencePort {
    ImagenPublicacion guardar(ImagenPublicacion imagen);
    Optional<ImagenPublicacion> buscar(UUID publicacionId, UUID imagenId);
    List<ImagenPublicacion> listarPorPublicacion(UUID publicacionId);
    boolean eliminar(UUID publicacionId, UUID imagenId);
}
