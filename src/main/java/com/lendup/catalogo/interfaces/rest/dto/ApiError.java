package com.lendup.catalogo.interfaces.rest.dto;

import java.time.Instant;
import java.util.Map;

public record ApiError(String codigo, String mensaje, Instant instante,
        Map<String, String> campos) {
    public static ApiError de(String codigo, String mensaje, Map<String, String> campos) {
        return new ApiError(codigo, mensaje, Instant.now(), Map.copyOf(campos));
    }
}
