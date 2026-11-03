package com.lendup.catalogo.application.port.out;
import java.util.UUID;
public interface ImagenStoragePort {
    record Almacenada(String publicId, String url) {}
    Almacenada subir(UUID publicacionId, byte[] contenido);
    void eliminar(String publicId);
}
