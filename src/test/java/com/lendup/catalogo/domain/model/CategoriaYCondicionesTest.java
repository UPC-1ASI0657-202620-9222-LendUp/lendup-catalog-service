package com.lendup.catalogo.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoriaYCondicionesTest {
    private CondicionesPublicacion condiciones(BigDecimal tarifa, BigDecimal garantia, String moneda) {
        return new CondicionesPublicacion(tarifa, garantia, moneda, "Campus",
                "Uso cuidadoso", "Entrega en campus", "Devolución en campus", "Aviso previo");
    }

    @Test void categoriaExigeCamposYRespetaLongitudesDelEsquema() {
        assertThrows(IllegalArgumentException.class, () -> new Categoria(UUID.randomUUID(), " ", "Cámaras", true));
        assertThrows(IllegalArgumentException.class, () -> new Categoria(UUID.randomUUID(), "X".repeat(51), "Cámaras", true));
        var categoria = new Categoria(UUID.randomUUID(), "TEC", "Tecnología", true);
        assertTrue(categoria.activa());
        assertEquals("TEC", categoria.codigo());
    }

    @Test void tarifaNoNegativaYGarantiaOpcional() {
        assertNull(condiciones(new BigDecimal("0.00"), null, "PEN").garantiaMonetaria());
        assertThrows(IllegalArgumentException.class,
                () -> condiciones(new BigDecimal("-0.01"), null, "PEN"));
        assertThrows(IllegalArgumentException.class,
                () -> condiciones(BigDecimal.ONE, new BigDecimal("-5"), "PEN"));
        assertThrows(IllegalArgumentException.class,
                () -> condiciones(BigDecimal.ONE, null, "SOL"));
        assertThrows(IllegalArgumentException.class,
                () -> condiciones(BigDecimal.ONE, null, "pe"));
    }

    @Test void datosPublicacionRespetanCamposObligatoriosYCoordenadas() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new DetallesPublicacion(id, "", "Descripción", "Bueno", "UPC", "Monterrico",
                        "Campus", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DetallesPublicacion(id, "Cámara", "Descripción", "Bueno", "UPC", "Monterrico",
                        "Campus", new BigDecimal("91"), null));
        var detalles = new DetallesPublicacion(id, "Cámara", "Descripción", "Bueno", "UPC", "Monterrico",
                "Campus", new BigDecimal("-12.05"), new BigDecimal("-77.05"));
        assertEquals("Cámara", detalles.titulo());
    }
}
