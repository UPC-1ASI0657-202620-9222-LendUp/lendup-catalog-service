package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CategoriaPersistenceAdapter implements CategoriaPersistencePort {
    private final CategoriaJpaRepository repository;

    public CategoriaPersistenceAdapter(CategoriaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Categoria> buscarPorId(UUID id) {
        return repository.findById(Objects.requireNonNull(id, "id").toString())
                .map(entity -> entity.aDominio());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Categoria> listarActivas() {
        return repository.findByActivaTrueOrderByNombreAsc().stream()
                .map(entity -> entity.aDominio()).toList();
    }

    @Override
    @Transactional
    public Categoria guardar(Categoria categoria) {
        Objects.requireNonNull(categoria, "categoria");
        var existente = repository.findById(categoria.id().toString());
        if (existente.isPresent()) {
            existente.get().actualizarDesde(categoria);
        } else {
            repository.save(new com.lendup.catalogo.infrastructure.persistence.entity.CategoriaEntity(categoria));
        }
        return categoria;
    }
}
