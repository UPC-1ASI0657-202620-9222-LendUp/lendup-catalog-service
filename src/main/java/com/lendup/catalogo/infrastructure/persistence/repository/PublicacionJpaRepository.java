package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.infrastructure.persistence.entity.PublicacionEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicacionJpaRepository extends JpaRepository<PublicacionEntity, String> {
    // Filtra también las categorías inactivas como lo hacía la búsqueda del monolito.
    @Query(value = """
            SELECT p.* FROM publicaciones p JOIN categorias c ON c.id = p.categoria_id
            WHERE p.estado = 'ACTIVA' AND c.activa = TRUE
            ORDER BY p.creado_en DESC, p.id DESC
            """, nativeQuery = true)
    List<PublicacionEntity> buscarPublicasActivas();

    List<PublicacionEntity> findByPropietarioUsuarioIdOrderByCreadoEnDescIdDesc(String propietarioUsuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PublicacionEntity p WHERE p.id = :id")
    Optional<PublicacionEntity> buscarPorIdConBloqueo(@Param("id") String id);
}
