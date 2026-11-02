package com.lendup.catalogo.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PeriodoDisponibilidadTest {
    private static LocalDateTime fecha(int dia, int hora) {
        return LocalDateTime.of(2026, 11, dia, hora, 0);
    }

    @Test void rechazaPeriodosVaciosInvertidosYNulos() {
        assertThrows(IllegalArgumentException.class, () -> new PeriodoDisponibilidad(fecha(2, 9), fecha(2, 9)));
        assertThrows(IllegalArgumentException.class, () -> new PeriodoDisponibilidad(fecha(3, 9), fecha(2, 9)));
        assertThrows(NullPointerException.class, () -> new PeriodoDisponibilidad(null, fecha(3, 9)));
    }

    @Test void permitePeriodosContiguosSinSolapamiento() {
        var primero = new PeriodoDisponibilidad(fecha(2, 9), fecha(2, 12));
        var segundo = new PeriodoDisponibilidad(fecha(2, 12), fecha(2, 15));
        assertFalse(primero.seSuperpone(segundo));
        assertFalse(segundo.seSuperpone(primero));
    }

    @Test void detectaSolapamientoYContencionDeExtremos() {
        var total = new PeriodoDisponibilidad(fecha(2, 9), fecha(2, 17));
        var parcial = new PeriodoDisponibilidad(fecha(2, 16), fecha(3, 10));
        var interior = new PeriodoDisponibilidad(fecha(2, 10), fecha(2, 15));
        assertTrue(total.seSuperpone(parcial));
        assertTrue(total.contiene(interior));
        assertTrue(total.contiene(total));
        assertFalse(total.contiene(parcial));
    }
}
