package com.lendup.catalogo.application.port.out;

import com.lendup.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaPersistencePort {
    Optional<Categoria> buscarPorId(UUID id);
    List<Categoria> listarActivas();
    Categoria guardar(Categoria categoria);
}
