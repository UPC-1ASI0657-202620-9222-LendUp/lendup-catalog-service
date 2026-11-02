package com.lendup.catalogo.application.service;

import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.ConsultarCategoriasUseCase;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaApplicationService implements ConsultarCategoriasUseCase {
    private final CategoriaPersistencePort categorias;

    public CategoriaApplicationService(CategoriaPersistencePort categorias) {
        this.categorias = categorias;
    }

    @Override
    @Transactional(readOnly = true)
    public Categoria obtener(UUID categoriaId) {
        return categorias.buscarPorId(Objects.requireNonNull(categoriaId, "categoriaId"))
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría", categoriaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Categoria> listarActivas() {
        return categorias.listarActivas();
    }
}
