package com.lendup.catalogo.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicacionTest {
    private final UUID publicacionId = UUID.randomUUID();
    private final UUID propietarioId = UUID.randomUUID();

    private DetallesPublicacion detalles() {
        return new DetallesPublicacion(UUID.randomUUID(), "Cámara", "Cámara para laboratorio",
                "Buen estado", "UPC", "Monterrico", "Campus", null, null);
    }

    private CondicionesPublicacion condiciones() {
        return new CondicionesPublicacion(new BigDecimal("15.00"), null, "PEN",
                "Campus", "Usar con cuidado", "Entrega presencial",
                "Devolver limpia", "Avisar antes");
    }

    private Publicacion nueva() {
        return Publicacion.nueva(publicacionId, propietarioId, detalles(), condiciones());
    }

    private PeriodoDisponibilidad periodo(int desdeHora, int hastaHora) {
        var dia = LocalDateTime.of(2026, 11, 2, 0, 0);
        return new PeriodoDisponibilidad(dia.plusHours(desdeHora), dia.plusHours(hastaHora));
    }

    private Disponibilidad slot(UUID id, int desde, int hasta) {
        return new Disponibilidad(id, publicacionId, periodo(desde, hasta));
    }

    @Test void creaPublicacionActivaConPropietarioExterno() {
        var pub = nueva();
        assertEquals(EstadoPublicacion.ACTIVA, pub.estado());
        assertTrue(pub.esPropietario(propietarioId));
        assertFalse(pub.esPropietario(UUID.randomUUID()));
        assertTrue(pub.disponibilidades().isEmpty());
        assertEquals("Cámara", pub.detalles().titulo());
    }

    @Test void permiteActualizarDatosYCambiarEntreEstadosExistentes() {
        var pub = nueva();
        pub.cambiarEstado(EstadoPublicacion.PAUSADA);
        assertFalse(pub.estado().esVisible());
        pub.cambiarEstado(EstadoPublicacion.DADA_DE_BAJA);
        pub.cambiarEstado(EstadoPublicacion.ACTIVA);
        assertTrue(pub.estado().esVisible());
        var actualizados = new DetallesPublicacion(UUID.randomUUID(), "Trípode", "Para cámara",
                "Bueno", "UPC", "San Miguel", "Campus", null, null);
        pub.actualizar(actualizados, condiciones());
        assertEquals("Trípode", pub.detalles().titulo());
    }

    @Test void aceptaIntervalosAdyacentesYRechazaSolapados() {
        var pub = nueva();
        pub.agregarDisponibilidad(slot(UUID.randomUUID(), 9, 12));
        pub.agregarDisponibilidad(slot(UUID.randomUUID(), 12, 15));
        assertEquals(2, pub.disponibilidades().size());
        assertThrows(IllegalArgumentException.class,
                () -> pub.agregarDisponibilidad(slot(UUID.randomUUID(), 11, 13)));
    }

    @Test void editarExcluyeSuPropioIdPeroNoPermiteInvadirOtroIntervalo() {
        var pub = nueva();
        UUID primero = UUID.randomUUID();
        pub.agregarDisponibilidad(slot(primero, 9, 12));
        pub.agregarDisponibilidad(slot(UUID.randomUUID(), 15, 18));
        pub.actualizarDisponibilidad(slot(primero, 8, 12));
        assertThrows(IllegalArgumentException.class,
                () -> pub.actualizarDisponibilidad(slot(primero, 14, 16)));
        assertEquals(periodo(8, 12), pub.disponibilidades().getFirst().periodo());
    }

    @Test void rechazaDisponibilidadDeOtraPublicacionEIdsDuplicados() {
        var pub = nueva();
        UUID id = UUID.randomUUID();
        pub.agregarDisponibilidad(slot(id, 9, 12));
        assertThrows(IllegalArgumentException.class, () -> pub.agregarDisponibilidad(slot(id, 15, 17)));
        assertThrows(IllegalArgumentException.class, () -> pub.agregarDisponibilidad(
                new Disponibilidad(UUID.randomUUID(), UUID.randomUUID(), periodo(15, 17))));
    }

    @Test void exigeUnUnicoIntervaloQueCubraTodoElPeriodoSolicitado() {
        var pub = nueva();
        pub.agregarDisponibilidad(slot(UUID.randomUUID(), 9, 12));
        pub.agregarDisponibilidad(slot(UUID.randomUUID(), 12, 15));
        assertTrue(pub.ofrecePeriodo(periodo(10, 12)));
        assertFalse(pub.ofrecePeriodo(periodo(11, 14))); // no se unen intervalos contiguos
        assertFalse(pub.ofrecePeriodo(periodo(8, 10)));
    }

    @Test void eliminarEsSeguroYLaVistaDeDisponibilidadesNoSePuedeModificar() {
        var pub = nueva();
        UUID id = UUID.randomUUID();
        pub.agregarDisponibilidad(slot(id, 9, 12));
        List<Disponibilidad> vista = pub.disponibilidades();
        assertThrows(UnsupportedOperationException.class, () -> vista.clear());
        pub.eliminarDisponibilidad(id);
        assertTrue(pub.disponibilidades().isEmpty());
        assertEquals(1, vista.size()); // copia defensiva
        assertThrows(NoSuchElementException.class, () -> pub.eliminarDisponibilidad(id));
    }

    @Test void reconstruccionDesdePersistenciaValidaSolapamientos() {
        var primero = slot(UUID.randomUUID(), 9, 12);
        var segundo = slot(UUID.randomUUID(), 11, 15);
        assertThrows(IllegalArgumentException.class,
                () -> new Publicacion(publicacionId, propietarioId, detalles(), condiciones(),
                        EstadoPublicacion.ACTIVA, List.of(primero, segundo)));
    }
}
