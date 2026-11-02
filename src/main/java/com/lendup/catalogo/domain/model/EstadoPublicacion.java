package com.lendup.catalogo.domain.model;

/** Valores existentes en publicaciones.estado del backend monolítico. */
public enum EstadoPublicacion {
    ACTIVA,
    PAUSADA,
    DADA_DE_BAJA;

    public boolean esVisible() {
        return this == ACTIVA;
    }
}
