package com.lendup.catalogo.domain.model;

import java.util.Objects;

/** Validaciones comunes del dominio, independientes de Spring y de la persistencia. */
final class ValidadorDominio {
    private ValidadorDominio() {}

    static String textoRequerido(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        String limpio = valor.trim();
        if (limpio.length() > longitudMaxima) {
            throw new IllegalArgumentException(campo + " supera " + longitudMaxima + " caracteres");
        }
        return limpio;
    }

    static <T> T requerido(T valor, String campo) {
        return Objects.requireNonNull(valor, campo + " es obligatorio");
    }
}
