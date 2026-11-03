package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.application.port.out.ImagenPublicacionPersistencePort;
import com.lendup.catalogo.domain.model.ImagenPublicacion;
import com.lendup.catalogo.infrastructure.persistence.entity.ImagenPublicacionEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ImagenPublicacionPersistenceAdapter implements ImagenPublicacionPersistencePort {
    private final ImagenPublicacionJpaRepository repository;

    public ImagenPublicacionPersistenceAdapter(ImagenPublicacionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ImagenPublicacion guardar(ImagenPublicacion imagen) {
        Objects.requireNonNull(imagen, "imagen");
        var actual = repository.findById(imagen.id().toString());
        if (actual.isPresent()) {
            actual.get().actualizarDesde(imagen);
        } else {
            repository.save(new ImagenPublicacionEntity(imagen));
        }
        repository.flush();
        return imagen;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImagenPublicacion> buscar(UUID publicacionId, UUID imagenId) {
        return repository.findByIdAndPublicacionId(
                Objects.requireNonNull(imagenId, "imagenId").toString(),
                Objects.requireNonNull(publicacionId, "publicacionId").toString())
                .map(ImagenPublicacionEntity::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImagenPublicacion> listarPorPublicacion(UUID publicacionId) {
        return repository.findByPublicacionIdOrderByOrdenAsc(
                Objects.requireNonNull(publicacionId, "publicacionId").toString())
                .stream().map(ImagenPublicacionEntity::aDominio).toList();
    }

    @Override
    @Transactional
    public boolean eliminar(UUID publicacionId, UUID imagenId) {
        var existente = repository.findByIdAndPublicacionId(
                Objects.requireNonNull(imagenId, "imagenId").toString(),
                Objects.requireNonNull(publicacionId, "publicacionId").toString());
        if (existente.isEmpty()) return false;
        repository.delete(existente.get());
        return true;
    }
}
