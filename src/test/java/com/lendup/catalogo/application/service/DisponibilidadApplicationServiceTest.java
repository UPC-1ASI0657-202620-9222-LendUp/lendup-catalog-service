package com.lendup.catalogo.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.GestionarDisponibilidadUseCase;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.DisponibilidadSuperpuestaException;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class DisponibilidadApplicationServiceTest {
    private final PublicacionPersistencePort repo = mock(PublicacionPersistencePort.class);
    private final DisponibilidadApplicationService service = new DisponibilidadApplicationService(repo);
    private final UUID propietario = UUID.randomUUID();
    private final UUID publicacionId = UUID.randomUUID();

    private Publicacion publicacion() {
        return Publicacion.nueva(publicacionId, propietario,
                new DetallesPublicacion(UUID.randomUUID(), "Lente", "Lente de cámara",
                        "Bueno", "UPC", "Monterrico", "Campus", null, null),
                new CondicionesPublicacion(new BigDecimal("10"), null, "PEN",
                        "Campus", "Cuidar", "En campus", "En campus", "Avisar"));
    }

    private PeriodoDisponibilidad periodo(int inicio, int fin) {
        var base = LocalDateTime.of(2026, 12, 2, 0, 0);
        return new PeriodoDisponibilidad(base.plusHours(inicio), base.plusHours(fin));
    }

    @Test void agregarConPropietarioGuardaAgregado() {
        var pub = publicacion();
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.of(pub));
        var resultado = service.agregar(new GestionarDisponibilidadUseCase.Agregar(
                publicacionId, propietario, periodo(9, 12)));
        assertEquals(publicacionId, resultado.publicacionId());
        assertEquals(1, pub.disponibilidades().size());
        verify(repo).guardar(same(pub));
    }

    @Test void rechazaSolapamientoSinGuardar() {
        var pub = publicacion();
        pub.agregarDisponibilidad(new Disponibilidad(UUID.randomUUID(), publicacionId, periodo(9, 12)));
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.of(pub));
        assertThrows(DisponibilidadSuperpuestaException.class, () -> service.agregar(
                new GestionarDisponibilidadUseCase.Agregar(publicacionId, propietario, periodo(10, 13))));
        assertEquals(1, pub.disponibilidades().size());
        verify(repo, never()).guardar(any());
    }

    @Test void actualizaPeriodoExcluyendoSuPropioId() {
        var pub = publicacion();
        UUID id = UUID.randomUUID();
        pub.agregarDisponibilidad(new Disponibilidad(id, publicacionId, periodo(9, 12)));
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.of(pub));
        var actualizado = service.actualizar(new GestionarDisponibilidadUseCase.Actualizar(
                publicacionId, id, propietario, periodo(8, 12)));
        assertEquals(id, actualizado.id());
        assertEquals(periodo(8, 12), pub.disponibilidades().getFirst().periodo());
        verify(repo).guardar(same(pub));
    }

    @Test void noModificaIntervaloDeIdInexistente() {
        var pub = publicacion();
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.of(pub));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(
                new GestionarDisponibilidadUseCase.Actualizar(
                        publicacionId, UUID.randomUUID(), propietario, periodo(8, 12))));
        verify(repo, never()).guardar(any());
    }

    @Test void eliminaIntervaloPropioYRechazaSegundoBorrado() {
        var pub = publicacion();
        UUID id = UUID.randomUUID();
        pub.agregarDisponibilidad(new Disponibilidad(id, publicacionId, periodo(9, 12)));
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.of(pub));
        service.eliminar(new GestionarDisponibilidadUseCase.Eliminar(publicacionId, id, propietario));
        assertTrue(pub.disponibilidades().isEmpty());
        verify(repo).guardar(same(pub));
        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminar(
                new GestionarDisponibilidadUseCase.Eliminar(publicacionId, id, propietario)));
    }

    @Test void usuarioDistintoNoPuedeAgregarActualizarNiEliminar() {
        var pub = publicacion();
        UUID otro = UUID.randomUUID();
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.of(pub));
        assertThrows(AccesoDenegadoException.class, () -> service.agregar(
                new GestionarDisponibilidadUseCase.Agregar(publicacionId, otro, periodo(9, 12))));
        assertThrows(AccesoDenegadoException.class, () -> service.actualizar(
                new GestionarDisponibilidadUseCase.Actualizar(
                        publicacionId, UUID.randomUUID(), otro, periodo(9, 12))));
        assertThrows(AccesoDenegadoException.class, () -> service.eliminar(
                new GestionarDisponibilidadUseCase.Eliminar(publicacionId, UUID.randomUUID(), otro)));
        verify(repo, never()).guardar(any());
    }

    @Test void listarRequiereSerPropietario() {
        var pub = publicacion();
        pub.agregarDisponibilidad(new Disponibilidad(UUID.randomUUID(), publicacionId, periodo(9, 12)));
        when(repo.buscarPorId(publicacionId)).thenReturn(Optional.of(pub));
        assertEquals(1, service.listar(publicacionId, propietario).size());
        assertThrows(AccesoDenegadoException.class, () -> service.listar(publicacionId, UUID.randomUUID()));
    }

    @Test void publicacionInexistenteNoModificaNada() {
        when(repo.buscarPorIdParaActualizar(publicacionId)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.agregar(
                new GestionarDisponibilidadUseCase.Agregar(publicacionId, propietario, periodo(9, 12))));
        verify(repo, never()).guardar(any());
    }

    @Test void escriturasAbrenTransaccion() throws Exception {
        assertFalse(DisponibilidadApplicationService.class.getMethod("agregar",
                GestionarDisponibilidadUseCase.Agregar.class).getAnnotation(Transactional.class).readOnly());
        assertFalse(DisponibilidadApplicationService.class.getMethod("actualizar",
                GestionarDisponibilidadUseCase.Actualizar.class).getAnnotation(Transactional.class).readOnly());
        assertFalse(DisponibilidadApplicationService.class.getMethod("eliminar",
                GestionarDisponibilidadUseCase.Eliminar.class).getAnnotation(Transactional.class).readOnly());
    }
}
