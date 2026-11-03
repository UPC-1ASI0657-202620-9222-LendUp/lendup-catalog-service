package com.lendup.catalogo.interfaces.rest.dto;
import com.lendup.catalogo.domain.model.ImagenPublicacion;
import java.util.UUID;
public record ImagenPublicacionResponse(UUID id,UUID publicacionId,String url,int orden) {
    public static ImagenPublicacionResponse deDominio(ImagenPublicacion imagen) {
        return new ImagenPublicacionResponse(imagen.id(),imagen.publicacionId(),
                imagen.url(),imagen.orden());
    }
}
