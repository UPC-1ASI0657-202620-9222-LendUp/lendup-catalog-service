package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.infrastructure.persistence.entity.ImagenPublicacionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenPublicacionJpaRepository extends JpaRepository<ImagenPublicacionEntity, String> {
    Optional<ImagenPublicacionEntity> findByIdAndPublicacionId(String id, String publicacionId);
    List<ImagenPublicacionEntity> findByPublicacionIdOrderByOrdenAsc(String publicacionId);
}
