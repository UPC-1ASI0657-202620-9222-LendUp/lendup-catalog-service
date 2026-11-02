package com.lendup.catalogo.infrastructure.persistence.repository;

import com.lendup.catalogo.application.port.out.DisponibilidadConsultaPort;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.specification.DisponibilidadSpecification;
import com.lendup.catalogo.infrastructure.persistence.entity.DisponibilidadEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DisponibilidadConsultaAdapter implements DisponibilidadConsultaPort {
    private final DisponibilidadJpaRepository repository;

    public DisponibilidadConsultaAdapter(DisponibilidadJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Disponibilidad> listarPorPublicacion(UUID publicacionId) {
        return repository.findByPublicacionIdOrderByDesdeAscIdAsc(
                Objects.requireNonNull(publicacionId, "publicacionId").toString())
                .stream().map(DisponibilidadEntity::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean periodoOfrecido(UUID publicacionId, PeriodoDisponibilidad solicitado) {
        return DisponibilidadSpecification.periodoOfrecido(listarPorPublicacion(publicacionId),
                Objects.requireNonNull(solicitado, "solicitado"));
    }
}
