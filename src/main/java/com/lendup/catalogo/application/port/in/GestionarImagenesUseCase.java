package com.lendup.catalogo.application.port.in;
import com.lendup.catalogo.domain.model.ImagenPublicacion;
import java.util.List;
import java.util.UUID;
public interface GestionarImagenesUseCase {
    ImagenPublicacion subir(UUID publicacionId, UUID actorId, byte[] archivo, String mime);
    List<ImagenPublicacion> listarPublicas(UUID publicacionId);
    void eliminar(UUID publicacionId, UUID imagenId, UUID actorId);
}
