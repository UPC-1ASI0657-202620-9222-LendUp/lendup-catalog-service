package com.lendup.catalogo.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.application.port.out.DisponibilidadConsultaPort;
import com.lendup.catalogo.application.port.out.ImagenPublicacionPersistencePort;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.ImagenPublicacion;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requiere MySQL local levantado con Docker Compose y DB_PASSWORD configurada.
 * Cada prueba corre en una transacción que Spring revierte al terminar.
 */
@SpringBootTest
@Transactional
class CatalogoPersistenceIntegrationTest {
    @Autowired private CategoriaPersistencePort categorias;
    @Autowired private PublicacionPersistencePort publicaciones;
    @Autowired private DisponibilidadConsultaPort disponibilidades;
    @Autowired private ImagenPublicacionPersistencePort imagenes;
    @Autowired private EntityManager entityManager;

    private Categoria categoria(boolean activa) {
        var categoria = new Categoria(UUID.randomUUID(), "COD-" + UUID.randomUUID().toString().substring(0, 8),
                "Categoría " + UUID.randomUUID().toString().substring(0, 8), activa);
        categorias.guardar(categoria);
        return categoria;
    }

    private Publicacion publicacion(Categoria categoria) {
        return Publicacion.nueva(UUID.randomUUID(), UUID.randomUUID(),
                new DetallesPublicacion(categoria.id(), "Proyector", "Proyector portátil",
                        "Buen estado", "UPC", "Monterrico", "Campus",
                        new BigDecimal("-12.1"), new BigDecimal("-77.1")),
                new CondicionesPublicacion(new BigDecimal("20.00"), null, "PEN",
                        "Entrada principal", "Cuidado normal", "Presencial", "Presencial", "Avisar"));
    }

    private PeriodoDisponibilidad periodo(int desdeHora, int hastaHora) {
        var dia = LocalDateTime.of(2026, 12, 1, 0, 0);
        return new PeriodoDisponibilidad(dia.plusHours(desdeHora), dia.plusHours(hastaHora));
    }

    @Test
    void guardaYReconstruyeAgregadoConDisponibilidadesYCondiciones() {
        var categoria = categoria(true);
        var pub = publicacion(categoria);
        pub.agregarDisponibilidad(new Disponibilidad(UUID.randomUUID(), pub.id(), periodo(8, 12)));
        publicaciones.guardar(pub);
        entityManager.flush();
        entityManager.clear();

        var recuperada = publicaciones.buscarPorId(pub.id()).orElseThrow();
        assertEquals(pub.id(), recuperada.id());
        assertEquals(pub.propietarioUsuarioId(), recuperada.propietarioUsuarioId());
        assertEquals("Proyector", recuperada.detalles().titulo());
        assertEquals(0, recuperada.condiciones().tarifaDiaria().compareTo(new BigDecimal("20.00")));
        assertNull(recuperada.condiciones().garantiaMonetaria());
        assertEquals(EstadoPublicacion.ACTIVA, recuperada.estado());
        assertEquals(1, recuperada.disponibilidades().size());
        assertTrue(disponibilidades.periodoOfrecido(pub.id(), periodo(9, 11)));
        assertFalse(disponibilidades.periodoOfrecido(pub.id(), periodo(11, 13)));
        assertTrue(publicaciones.listarPorPropietario(pub.propietarioUsuarioId()).stream()
                .anyMatch(p -> p.id().equals(pub.id())));
        assertTrue(publicaciones.listarActivas().stream().anyMatch(p -> p.id().equals(pub.id())));
    }

    @Test
    void modificaEstadoActualizaIntervaloYEliminaIntervaloConBloqueo() {
        var pub = publicacion(categoria(true));
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        pub.agregarDisponibilidad(new Disponibilidad(primera, pub.id(), periodo(8, 12)));
        pub.agregarDisponibilidad(new Disponibilidad(segunda, pub.id(), periodo(14, 18)));
        publicaciones.guardar(pub);
        entityManager.flush();
        entityManager.clear();

        var bloqueada = publicaciones.buscarPorIdParaActualizar(pub.id()).orElseThrow();
        bloqueada.cambiarEstado(EstadoPublicacion.PAUSADA);
        bloqueada.actualizarDisponibilidad(new Disponibilidad(primera, pub.id(), periodo(7, 12)));
        bloqueada.eliminarDisponibilidad(segunda);
        publicaciones.guardar(bloqueada);
        entityManager.flush();
        entityManager.clear();

        var guardada = publicaciones.buscarPorId(pub.id()).orElseThrow();
        assertEquals(EstadoPublicacion.PAUSADA, guardada.estado());
        assertEquals(1, guardada.disponibilidades().size());
        assertEquals(primera, guardada.disponibilidades().getFirst().id());
        assertEquals(periodo(7, 12), guardada.disponibilidades().getFirst().periodo());
        assertFalse(publicaciones.listarActivas().stream().anyMatch(p -> p.id().equals(pub.id())));
    }

    @Test
    void omitePublicacionesDeCategoriasInactivasYListaSoloCategoriasActivas() {
        var activa = categoria(true);
        var inactiva = categoria(false);
        var visible = publicacion(activa);
        var oculta = publicacion(inactiva);
        publicaciones.guardar(visible);
        publicaciones.guardar(oculta);
        entityManager.flush();
        entityManager.clear();

        assertTrue(categorias.listarActivas().stream().anyMatch(c -> c.id().equals(activa.id())));
        assertFalse(categorias.listarActivas().stream().anyMatch(c -> c.id().equals(inactiva.id())));
        assertTrue(publicaciones.listarActivas().stream().anyMatch(p -> p.id().equals(visible.id())));
        assertFalse(publicaciones.listarActivas().stream().anyMatch(p -> p.id().equals(oculta.id())));
    }

    @Test
    void persisteMetadatosDeImagenYValidaPertenenciaAlEliminar() {
        var pub = publicacion(categoria(true));
        publicaciones.guardar(pub);
        var imagen = new ImagenPublicacion(UUID.randomUUID(), pub.id(),
                "catalogo/foto1", "https://res.cloudinary.com/example/image/upload/foto1.jpg", 0);
        imagenes.guardar(imagen);
        entityManager.flush();
        entityManager.clear();

        assertEquals(imagen, imagenes.buscar(pub.id(), imagen.id()).orElseThrow());
        assertEquals(1, imagenes.listarPorPublicacion(pub.id()).size());
        assertTrue(imagenes.buscar(UUID.randomUUID(), imagen.id()).isEmpty());
        assertFalse(imagenes.eliminar(UUID.randomUUID(), imagen.id()));
        assertTrue(imagenes.eliminar(pub.id(), imagen.id()));
        entityManager.flush();
        entityManager.clear();
        assertTrue(imagenes.buscar(pub.id(), imagen.id()).isEmpty());
    }

    @Test
    void impideCambiarPropietarioDeUnaPublicacionPersistida() {
        var pub = publicacion(categoria(true));
        publicaciones.guardar(pub);
        var otro = Publicacion.nueva(pub.id(), UUID.randomUUID(), pub.detalles(), pub.condiciones());
        assertThrows(IllegalArgumentException.class, () -> publicaciones.guardar(otro));
    }
}
