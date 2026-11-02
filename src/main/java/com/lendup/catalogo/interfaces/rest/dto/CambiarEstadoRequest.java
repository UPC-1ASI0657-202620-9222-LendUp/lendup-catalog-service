package com.lendup.catalogo.interfaces.rest.dto;

import com.lendup.catalogo.domain.model.EstadoPublicacion;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull EstadoPublicacion estado) {}
