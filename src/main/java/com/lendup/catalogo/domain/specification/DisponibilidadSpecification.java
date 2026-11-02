package com.lendup.catalogo.domain.specification;

import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/** Reglas de disponibilidad ofrecida, sin consultas a la base de datos de Reservas. */
public final class DisponibilidadSpecification {
    private DisponibilidadSpecification() {}

    /** Reproduce el criterio del monolito: debe existir UN intervalo que cubra todo el periodo. */
    public static boolean periodoOfrecido(Collection<Disponibilidad> disponibilidades, PeriodoDisponibilidad solicitado) {
        Objects.requireNonNull(disponibilidades, "disponibilidades");
        Objects.requireNonNull(solicitado, "solicitado");
        return disponibilidades.stream().anyMatch(slot -> slot.periodo().contiene(solicitado));
    }

    /** Exclusión de un ID para permitir actualizar un intervalo sin solaparse consigo mismo. */
    public static boolean sinSolapamiento(Collection<Disponibilidad> disponibilidades,
            PeriodoDisponibilidad candidato, UUID idExcluido) {
        Objects.requireNonNull(disponibilidades, "disponibilidades");
        Objects.requireNonNull(candidato, "candidato");
        return disponibilidades.stream()
                .filter(slot -> !slot.id().equals(idExcluido))
                .noneMatch(slot -> slot.periodo().seSuperpone(candidato));
    }
}
