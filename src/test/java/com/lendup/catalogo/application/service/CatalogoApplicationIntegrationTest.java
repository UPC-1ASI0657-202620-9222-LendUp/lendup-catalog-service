package com.lendup.catalogo.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.port.in.ConsultarCategoriasUseCase;
import com.lendup.catalogo.application.port.in.ConsultarPublicacionesUseCase;
import com.lendup.catalogo.application.port.in.GestionarDisponibilidadUseCase;
import com.lendup.catalogo.application.port.in.GestionarPublicacionesUseCase;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Requiere DB_PASSWORD y el MySQL de Docker; rollback al terminar cada prueba. */
@SpringBootTest
@Transactional
class CatalogoApplicationIntegrationTest {
    @Autowired private GestionarPublicacionesUseCase gestion;
    @Autowired private ConsultarPublicacionesUseCase consultas;
    @Autowired private GestionarDisponibilidadUseCase disponibilidad;
    @Autowired private ConsultarCategoriasUseCase categoriasConsulta;
    @Autowired private CategoriaPersistencePort categorias;
    @Autowired private EntityManager entityManager;

    private Categoria categoria(boolean activa) {
        var valor = new Categoria(UUID.randomUUID(),
                "APP-" + UUID.randomUUID().toString().substring(0, 8),
                "Aplicación " + UUID.randomUUID().toString().substring(0, 8),
                activa);
        return categorias.guardar(valor);
    }

    private DetallesPublicacion detalles(Categoria categoria, String titulo) {
        return new DetallesPublicacion(categoria.id(), titulo, "Accesorio para clase",
                "Buen estado", "UPC", "Monterrico", "Campus", null, null);
    }

    private CondicionesPublicacion condiciones() {
        return new CondicionesPublicacion(new BigDecimal("15.00"), null, "PEN",
                "Campus", "Cuidar", "En campus", "En campus", "Avisar");
    }

    private PeriodoDisponibilidad periodo(int inicio, int fin) {
        var base = LocalDateTime.of(2026, 12, 3, 0, 0);
        return new PeriodoDisponibilidad(base.plusHours(inicio), base.plusHours(fin));
    }

    @Test void flujoCompletoGuardaYRecuperaPublicacionConDisponibilidades() {
        var categoria = categoria(true);
        UUID actor = UUID.randomUUID();
        var creada = gestion.crear(new GestionarPublicacionesUseCase.Crear(
                actor, detalles(categoria, "Micrófono"), condiciones()));
        var intervalo = disponibilidad.agregar(new GestionarDisponibilidadUseCase.Agregar(
                creada.id(), actor, periodo(8, 12)));
        var actualizado = disponibilidad.actualizar(new GestionarDisponibilidadUseCase.Actualizar(
                creada.id(), intervalo.id(), actor, periodo(7, 12)));
        assertEquals(intervalo.id(), actualizado.id());
        gestion.actualizar(new GestionarPublicacionesUseCase.Actualizar(
                creada.id(), actor, detalles(categoria, "Micrófono inalámbrico"), condiciones()));
        gestion.cambiarEstado(new GestionarPublicacionesUseCase.CambiarEstado(
                creada.id(), actor, EstadoPublicacion.PAUSADA));
        entityManager.flush();
        entityManager.clear();

        var guardada = consultas.obtener(creada.id());
        assertEquals("Micrófono inalámbrico", guardada.detalles().titulo());
        assertEquals(EstadoPublicacion.PAUSADA, guardada.estado());
        assertEquals(1, guardada.disponibilidades().size());
        assertTrue(consultas.periodoOfrecido(creada.id(), periodo(8, 11)));
        assertFalse(consultas.listarActivas().stream().anyMatch(p -> p.id().equals(creada.id())));
        assertTrue(consultas.listarMisPublicaciones(actor).stream().anyMatch(p -> p.id().equals(creada.id())));

        disponibilidad.eliminar(new GestionarDisponibilidadUseCase.Eliminar(
                creada.id(), intervalo.id(), actor));
        entityManager.flush();
        entityManager.clear();
        assertTrue(disponibilidad.listar(creada.id(), actor).isEmpty());
    }

    @Test void serviciosRespetanPropiedadYVisibilidadDeCategoria() {
        var categoria = categoria(false);
        UUID actor = UUID.randomUUID();
        var creada = gestion.crear(new GestionarPublicacionesUseCase.Crear(
                actor, detalles(categoria, "Tableta"), condiciones()));
        entityManager.flush();
        entityManager.clear();

        assertEquals(creada.id(), consultas.obtener(creada.id()).id());
        assertFalse(consultas.listarActivas().stream().anyMatch(p -> p.id().equals(creada.id())));
        assertFalse(categoriasConsulta.listarActivas().stream().anyMatch(c -> c.id().equals(categoria.id())));
        assertThrows(AccesoDenegadoException.class, () -> disponibilidad.listar(
                creada.id(), UUID.randomUUID()));
    }
}
