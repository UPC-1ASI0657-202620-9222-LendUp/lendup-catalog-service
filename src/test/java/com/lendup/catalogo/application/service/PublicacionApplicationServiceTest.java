package com.lendup.catalogo.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.GestionarPublicacionesUseCase;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PublicacionApplicationServiceTest {
    private final PublicacionPersistencePort publicaciones = mock(PublicacionPersistencePort.class);
    private final CategoriaPersistencePort categorias = mock(CategoriaPersistencePort.class);
    private final PublicacionApplicationService service =
            new PublicacionApplicationService(publicaciones, categorias);
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID propietarioId = UUID.randomUUID();

    private DetallesPublicacion detalles() {
        return new DetallesPublicacion(categoriaId, "Trípode", "Equipo para fotos",
                "Bueno", "UPC", "Monterrico", "Campus", null, null);
    }

    private CondicionesPublicacion condiciones() {
        return new CondicionesPublicacion(new BigDecimal("12.00"), null, "PEN",
                "Campus", "Cuidar", "En mano", "En mano", "Avisar");
    }

    private Publicacion publicacion() {
        return Publicacion.nueva(UUID.randomUUID(), propietarioId, detalles(), condiciones());
    }

    private void categoriaExistente() {
        when(categorias.buscarPorId(categoriaId))
                .thenReturn(Optional.of(new Categoria(categoriaId, "TEC", "Tecnología", true)));
    }

    @Test void crearGeneraIdYPropietarioYLlamaAlRepositorio() {
        categoriaExistente();
        when(publicaciones.guardar(any(Publicacion.class))).thenAnswer(call -> call.getArgument(0));

        var creada = service.crear(new GestionarPublicacionesUseCase.Crear(
                propietarioId, detalles(), condiciones()));
        assertNotNull(creada.id());
        assertEquals(propietarioId, creada.propietarioUsuarioId());
        assertEquals(EstadoPublicacion.ACTIVA, creada.estado());
        verify(publicaciones).guardar(same(creada));
    }

    @Test void noCreaSiFaltaCategoria() {
        when(categorias.buscarPorId(categoriaId)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                new GestionarPublicacionesUseCase.Crear(propietarioId, detalles(), condiciones())));
        verify(publicaciones, never()).guardar(any());
    }

    @Test void actualizarUtilizaBloqueoYConservaPropietario() {
        var pub = publicacion();
        when(publicaciones.buscarPorIdParaActualizar(pub.id())).thenReturn(Optional.of(pub));
        categoriaExistente();
        when(publicaciones.guardar(any())).thenAnswer(call -> call.getArgument(0));
        var actualizados = new DetallesPublicacion(categoriaId, "Trípode reforzado", "Equipo para fotos",
                "Bueno", "UPC", "Monterrico", "Campus", null, null);

        var resultado = service.actualizar(new GestionarPublicacionesUseCase.Actualizar(
                pub.id(), propietarioId, actualizados, condiciones()));

        assertEquals("Trípode reforzado", resultado.detalles().titulo());
        assertEquals(propietarioId, resultado.propietarioUsuarioId());
        verify(publicaciones).buscarPorIdParaActualizar(pub.id());
        verify(publicaciones).guardar(same(pub));
        verify(publicaciones, never()).buscarPorId(pub.id());
    }

    @Test void bloqueaActualizacionDeActorDistintoAntesDeBuscarCategoria() {
        var pub = publicacion();
        when(publicaciones.buscarPorIdParaActualizar(pub.id())).thenReturn(Optional.of(pub));
        assertThrows(AccesoDenegadoException.class, () -> service.actualizar(
                new GestionarPublicacionesUseCase.Actualizar(
                        pub.id(), UUID.randomUUID(), detalles(), condiciones())));
        verifyNoInteractions(categorias);
        verify(publicaciones, never()).guardar(any());
    }

    @Test void rechazaCategoriaInexistenteEnActualizacionSinMutarAgregado() {
        var pub = publicacion();
        when(publicaciones.buscarPorIdParaActualizar(pub.id())).thenReturn(Optional.of(pub));
        when(categorias.buscarPorId(categoriaId)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(
                new GestionarPublicacionesUseCase.Actualizar(
                        pub.id(), propietarioId, detalles(), condiciones())));
        assertEquals("Trípode", pub.detalles().titulo());
        verify(publicaciones, never()).guardar(any());
    }

    @Test void cambiarEstadoRequierePropietarioYSinCategoriaAdicional() {
        var pub = publicacion();
        when(publicaciones.buscarPorIdParaActualizar(pub.id())).thenReturn(Optional.of(pub));
        when(publicaciones.guardar(any())).thenAnswer(call -> call.getArgument(0));
        var pausada = service.cambiarEstado(
                new GestionarPublicacionesUseCase.CambiarEstado(
                        pub.id(), propietarioId, EstadoPublicacion.PAUSADA));
        assertEquals(EstadoPublicacion.PAUSADA, pausada.estado());
        verifyNoInteractions(categorias);
    }

    @Test void cambiarEstadoDeOtroUsuarioDenegado() {
        var pub = publicacion();
        when(publicaciones.buscarPorIdParaActualizar(pub.id())).thenReturn(Optional.of(pub));
        assertThrows(AccesoDenegadoException.class, () -> service.cambiarEstado(
                new GestionarPublicacionesUseCase.CambiarEstado(
                        pub.id(), UUID.randomUUID(), EstadoPublicacion.DADA_DE_BAJA)));
        assertEquals(EstadoPublicacion.ACTIVA, pub.estado());
        verify(publicaciones, never()).guardar(any());
    }

    @Test void consultasNoCambianEstadoYListarMiasFiltraPorActor() {
        var pub = publicacion();
        when(publicaciones.buscarPorId(pub.id())).thenReturn(Optional.of(pub));
        when(publicaciones.listarActivas()).thenReturn(List.of(pub));
        when(publicaciones.listarPorPropietario(propietarioId)).thenReturn(List.of(pub));
        assertEquals(pub.id(), service.obtener(pub.id()).id());
        assertEquals(1, service.listarActivas().size());
        assertEquals(1, service.listarMisPublicaciones(propietarioId).size());
        verify(publicaciones).listarPorPropietario(propietarioId);
        verify(publicaciones, never()).guardar(any());
    }

    @Test void periodoOfrecidoSoloCompruebaOfertaYNoReservas() {
        var pub = publicacion();
        var desde = LocalDateTime.of(2026, 12, 1, 9, 0);
        pub.agregarDisponibilidad(new Disponibilidad(UUID.randomUUID(), pub.id(),
                new PeriodoDisponibilidad(desde, desde.plusHours(4))));
        when(publicaciones.buscarPorId(pub.id())).thenReturn(Optional.of(pub));
        assertTrue(service.periodoOfrecido(pub.id(),
                new PeriodoDisponibilidad(desde.plusHours(1), desde.plusHours(2))));
        assertFalse(service.periodoOfrecido(pub.id(),
                new PeriodoDisponibilidad(desde.minusHours(1), desde.plusHours(2))));
    }

    @Test void noEncontradaEnConsultaYModificacion() {
        UUID noExiste = UUID.randomUUID();
        when(publicaciones.buscarPorId(noExiste)).thenReturn(Optional.empty());
        when(publicaciones.buscarPorIdParaActualizar(noExiste)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.obtener(noExiste));
        assertThrows(RecursoNoEncontradoException.class, () -> service.cambiarEstado(
                new GestionarPublicacionesUseCase.CambiarEstado(
                        noExiste, propietarioId, EstadoPublicacion.PAUSADA)));
    }

    @Test void operacionesDeEscrituraTienenTransaccion() throws Exception {
        assertFalse(PublicacionApplicationService.class.getMethod("crear",
                GestionarPublicacionesUseCase.Crear.class).getAnnotation(Transactional.class).readOnly());
        assertFalse(PublicacionApplicationService.class.getMethod("actualizar",
                GestionarPublicacionesUseCase.Actualizar.class).getAnnotation(Transactional.class).readOnly());
        assertFalse(PublicacionApplicationService.class.getMethod("cambiarEstado",
                GestionarPublicacionesUseCase.CambiarEstado.class).getAnnotation(Transactional.class).readOnly());
    }
}
