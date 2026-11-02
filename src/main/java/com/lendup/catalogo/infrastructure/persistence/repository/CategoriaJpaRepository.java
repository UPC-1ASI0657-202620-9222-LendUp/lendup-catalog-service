package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.infrastructure.persistence.entity.CategoriaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaJpaRepository extends JpaRepository<CategoriaEntity, String> {
    List<CategoriaEntity> findByActivaTrueOrderByNombreAsc();
}
