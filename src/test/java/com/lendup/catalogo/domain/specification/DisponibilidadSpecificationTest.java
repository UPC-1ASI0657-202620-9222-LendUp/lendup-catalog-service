package com.lendup.catalogo.domain.specification;

import static org.junit.jupiter.api.Assertions.*;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisponibilidadSpecificationTest {
    private PeriodoDisponibilidad periodo(int inicio, int fin) {
        var dia = LocalDateTime.of(2026, 11, 2, 0, 0);
        return new PeriodoDisponibilidad(dia.plusHours(inicio), dia.plusHours(fin));
    }

    @Test void verificaCoberturaYExcluyeIdAlActualizar() {
        UUID id = UUID.randomUUID();
        var disponibilidad = new Disponibilidad(id, UUID.randomUUID(), periodo(8, 12));
        var existentes = List.of(disponibilidad);
        assertTrue(DisponibilidadSpecification.periodoOfrecido(existentes, periodo(9, 10)));
        assertFalse(DisponibilidadSpecification.periodoOfrecido(existentes, periodo(11, 13)));
        assertFalse(DisponibilidadSpecification.sinSolapamiento(existentes, periodo(9, 10), null));
        assertTrue(DisponibilidadSpecification.sinSolapamiento(existentes, periodo(9, 10), id));
        assertTrue(DisponibilidadSpecification.sinSolapamiento(existentes, periodo(12, 14), null));
    }
}
