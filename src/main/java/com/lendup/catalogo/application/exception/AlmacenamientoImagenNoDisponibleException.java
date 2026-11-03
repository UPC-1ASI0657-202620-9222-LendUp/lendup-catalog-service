package com.lendup.catalogo.application.exception;
public class AlmacenamientoImagenNoDisponibleException extends RuntimeException {
    public AlmacenamientoImagenNoDisponibleException() {
        super("El almacenamiento de imágenes no está disponible");
    }
    public AlmacenamientoImagenNoDisponibleException(Throwable causa) {
        super("El almacenamiento de imágenes no está disponible", causa);
    }
}
