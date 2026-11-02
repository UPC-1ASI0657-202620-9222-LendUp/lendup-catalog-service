package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.infrastructure.persistence.entity.DisponibilidadEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisponibilidadJpaRepository extends JpaRepository<DisponibilidadEntity, String> {
    List<DisponibilidadEntity> findByPublicacionIdOrderByDesdeAscIdAsc(String publicacionId);
}
