package com.lendup.catalogo.application.port.in;

import com.lendup.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.UUID;

public interface ConsultarCategoriasUseCase {
    Categoria obtener(UUID categoriaId);
    List<Categoria> listarActivas();
}
