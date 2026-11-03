package com.lendup.catalogo.interfaces.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.ConsultarCategoriasUseCase;
import com.lendup.catalogo.application.port.in.ConsultarPublicacionesUseCase;
import com.lendup.catalogo.application.port.in.GestionarDisponibilidadUseCase;
import com.lendup.catalogo.application.port.in.GestionarPublicacionesUseCase;
import com.lendup.catalogo.application.port.out.FirebaseTokenVerifier;
import com.lendup.catalogo.application.port.out.IdentidadUsuarioPort;
import com.lendup.catalogo.config.CatalogoSecurityConfig;
import com.lendup.catalogo.config.UsuarioActual;
import com.lendup.catalogo.config.UsuarioAutenticado;
import com.lendup.catalogo.domain.model.Categoria;
import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.Disponibilidad;
import com.lendup.catalogo.domain.model.DisponibilidadSuperpuestaException;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.PeriodoDisponibilidad;
import com.lendup.catalogo.domain.model.Publicacion;
import com.lendup.catalogo.interfaces.rest.dto.CondicionesRequest;
import com.lendup.catalogo.interfaces.rest.dto.DetallesRequest;
import com.lendup.catalogo.interfaces.rest.dto.PublicacionRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Verifica los controladores con Spring MVC, Jackson 3 y filtros de seguridad activos. */
@WebMvcTest(controllers = {CategoriaController.class, PublicacionController.class,
        DisponibilidadController.class, ImagenPublicacionController.class})
@Import({CatalogoSecurityConfig.class, UsuarioActual.class,
        ParchePublicacion.class, CatalogoExceptionHandler.class})
class CatalogoRestControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;
    @MockitoBean private GestionarPublicacionesUseCase gestion;
    @MockitoBean private ConsultarPublicacionesUseCase consultas;
    @MockitoBean private ConsultarCategoriasUseCase categorias;
    @MockitoBean private GestionarDisponibilidadUseCase disponibilidad;
    @MockitoBean private com.lendup.catalogo.application.port.in.GestionarImagenesUseCase gestionImagenes;
    @MockitoBean private FirebaseTokenVerifier firebaseVerificador;
    @MockitoBean private IdentidadUsuarioPort identidadUsuarios;

    private final UUID actor = UUID.randomUUID();
    private final UUID categoriaId = UUID.randomUUID();

    private RequestPostProcessor autenticado(UUID id) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(id), "VERIFICADO_POR_IDENTIDAD",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Categoria categoria(boolean activa) {
        return new Categoria(categoriaId, "CAT", "Equipo", activa);
    }

    private Publicacion publicacion() {
        return Publicacion.nueva(UUID.randomUUID(), actor,
                new DetallesPublicacion(categoriaId, "Cámara", "Cámara para clases",
                        "Buen estado", "UPC", "Monterrico", "Campus", null, null),
                new CondicionesPublicacion(new BigDecimal("25.00"), new BigDecimal("100.00"),
                        "PEN", "Campus", "Cuidar", "En mano", "En mano", "Avisar"));
    }

    private String solicitud() throws Exception {
        var pub = publicacion();
        return json.writeValueAsString(PublicacionRequest.deDominio(pub));
    }

    private PeriodoDisponibilidad intervalo() {
        var desde = LocalDateTime.of(2026, 12, 12, 9, 0);
        return new PeriodoDisponibilidad(desde, desde.plusHours(3));
    }

    @Test void lecturaPublicaDeCategorias() throws Exception {
        when(categorias.listarActivas()).thenReturn(List.of(categoria(true)));
        mvc.perform(get("/api/v1/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Equipo"));
        when(categorias.obtener(categoriaId)).thenReturn(categoria(false));
        mvc.perform(get("/api/v1/catalogo/categorias/{id}", categoriaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void listadoSoloUsaConsultasPublicas() throws Exception {
        var pub = publicacion();
        when(consultas.listarActivas()).thenReturn(List.of(pub));
        mvc.perform(get("/api/v1/catalogo/publicaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pub.id().toString()))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"));
    }

    @Test void detallePublicoOcultaPublicacionPausada() throws Exception {
        var pub = publicacion();
        pub.cambiarEstado(EstadoPublicacion.PAUSADA);
        when(consultas.obtener(pub.id())).thenReturn(pub);
        mvc.perform(get("/api/v1/catalogo/publicaciones/{id}", pub.id()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(categorias);
    }

    @Test void detallePublicoOcultaCategoriaInactiva() throws Exception {
        var pub = publicacion();
        when(consultas.obtener(pub.id())).thenReturn(pub);
        when(categorias.obtener(categoriaId)).thenReturn(categoria(false));
        mvc.perform(get("/api/v1/catalogo/publicaciones/{id}", pub.id()))
                .andExpect(status().isNotFound());
    }

    @Test void detallePublicoDePublicacionVisible() throws Exception {
        var pub = publicacion();
        when(consultas.obtener(pub.id())).thenReturn(pub);
        when(categorias.obtener(categoriaId)).thenReturn(categoria(true));
        mvc.perform(get("/api/v1/catalogo/publicaciones/{id}", pub.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles.titulo").value("Cámara"));
    }

    @Test void sinAutenticacionNoPuedeCrearNiListarPrivadas() throws Exception {
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud())
                        .header("X-User-Id", actor))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/catalogo/mis-publicaciones")
                        .header("X-User-Id", actor))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gestion);
    }

    @Test void usuarioGenericoAutenticadoNoEquivaleAUuidVerificado() throws Exception {
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .with(user("cualquiera"))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(gestion);
    }

    @Test void crearCorrectamenteDevuelve201YLocation() throws Exception {
        var pub = publicacion();
        when(gestion.crear(any())).thenReturn(pub);
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/catalogo/publicaciones/" + pub.id()))
                .andExpect(jsonPath("$.id").value(pub.id().toString()));
        ArgumentCaptor<GestionarPublicacionesUseCase.Crear> captor =
                ArgumentCaptor.forClass(GestionarPublicacionesUseCase.Crear.class);
        verify(gestion).crear(captor.capture());
        assertEquals(actor, captor.getValue().actorUsuarioId());
    }

    @Test void solicitudesInvalidasDevuelven400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalogo/categorias/no-es-uuid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gestion);
    }

    @Test void putReemplazaDatosCompletos() throws Exception {
        var pub = publicacion();
        when(gestion.actualizar(any())).thenReturn(pub);
        mvc.perform(put("/api/v1/catalogo/publicaciones/{id}", pub.id())
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pub.id().toString()));
    }

    @Test void patchFusionaCamposYPermiteBorrarGarantia() throws Exception {
        var pub = publicacion();
        when(consultas.obtener(pub.id())).thenReturn(pub);
        when(gestion.actualizar(any())).thenAnswer(inv -> {
            GestionarPublicacionesUseCase.Actualizar comando = inv.getArgument(0);
            pub.actualizar(comando.detalles(), comando.condiciones());
            return pub;
        });
        mvc.perform(patch("/api/v1/catalogo/publicaciones/{id}", pub.id())
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"detalles":{"titulo":"Cámara HD"},
                             "condiciones":{"garantiaMonetaria":null}}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles.titulo").value("Cámara HD"))
                .andExpect(jsonPath("$.condiciones.garantiaMonetaria").value(
                        org.hamcrest.Matchers.nullValue()));
        ArgumentCaptor<GestionarPublicacionesUseCase.Actualizar> captor =
                ArgumentCaptor.forClass(GestionarPublicacionesUseCase.Actualizar.class);
        verify(gestion).actualizar(captor.capture());
        assertEquals(actor, captor.getValue().actorUsuarioId());
        assertEquals("Cámara HD", captor.getValue().detalles().titulo());
        assertNull(captor.getValue().condiciones().garantiaMonetaria());
        assertEquals("UPC", captor.getValue().detalles().universidad());
    }

    @Test void patchRechazaCamposDesconocidosYOtroPropietario() throws Exception {
        var pub = publicacion();
        when(consultas.obtener(pub.id())).thenReturn(pub);
        mvc.perform(patch("/api/v1/catalogo/publicaciones/{id}", pub.id())
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propietarioUsuarioId\":\""+actor+"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        mvc.perform(patch("/api/v1/catalogo/publicaciones/{id}", pub.id())
                        .with(autenticado(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalles\":{\"titulo\":\"Otro\"}}"))
                .andExpect(status().isForbidden());
        verify(gestion, never()).actualizar(any());
    }

    @Test void cambioDeEstadoYListadoDelPropietario() throws Exception {
        var pub = publicacion();
        pub.cambiarEstado(EstadoPublicacion.PAUSADA);
        when(gestion.cambiarEstado(any())).thenReturn(pub);
        when(consultas.listarMisPublicaciones(actor)).thenReturn(List.of(pub));
        mvc.perform(patch("/api/v1/catalogo/publicaciones/{id}/estado", pub.id())
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PAUSADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADA"));
        mvc.perform(get("/api/v1/catalogo/mis-publicaciones")
                        .with(autenticado(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PAUSADA"));
    }

    @Test void disponibilidadCreaYEliminaConIdentidadVerificada() throws Exception {
        var pub = publicacion();
        var i = new Disponibilidad(UUID.randomUUID(), pub.id(), intervalo());
        when(disponibilidad.agregar(any())).thenReturn(i);
        mvc.perform(post("/api/v1/catalogo/publicaciones/{id}/disponibilidades", pub.id())
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desde\":\"2026-12-12T09:00:00\",\"hasta\":\"2026-12-12T12:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(i.id().toString()));
        mvc.perform(delete("/api/v1/catalogo/publicaciones/{id}/disponibilidades/{intervalo}", pub.id(), i.id())
                        .with(autenticado(actor)))
                .andExpect(status().isNoContent());
        ArgumentCaptor<GestionarDisponibilidadUseCase.Eliminar> captor =
                ArgumentCaptor.forClass(GestionarDisponibilidadUseCase.Eliminar.class);
        verify(disponibilidad).eliminar(captor.capture());
        assertEquals(actor, captor.getValue().actorUsuarioId());
    }

    @Test void disponibilidadRechazaPeriodoInvalidoYColision() throws Exception {
        var pub = publicacion();
        var uri = "/api/v1/catalogo/publicaciones/" + pub.id() + "/disponibilidades";
        mvc.perform(post(uri).with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desde\":\"2026-12-12T12:00:00\",\"hasta\":\"2026-12-12T09:00:00\"}"))
                .andExpect(status().isBadRequest());
        verify(disponibilidad, never()).agregar(any());
        when(disponibilidad.agregar(any())).thenThrow(new DisponibilidadSuperpuestaException());
        mvc.perform(post(uri).with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desde\":\"2026-12-12T09:00:00\",\"hasta\":\"2026-12-12T12:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DISPONIBILIDAD_SUPERPUESTA"));
    }

    @Test void erroresDeRecursoYPropietarioTienenCodigosEstables() throws Exception {
        var pub = publicacion();
        when(gestion.cambiarEstado(any())).thenThrow(new AccesoDenegadoException());
        mvc.perform(patch("/api/v1/catalogo/publicaciones/{id}/estado", pub.id())
                        .with(autenticado(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PAUSADA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        when(consultas.obtener(pub.id()))
                .thenThrow(new RecursoNoEncontradoException("Publicación", pub.id()));
        mvc.perform(get("/api/v1/catalogo/publicaciones/{id}", pub.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void bearerValidadoResuelveUuidDelPropietarioEnSolicitudHttp() throws Exception {
        var pub = publicacion();
        UUID interno = UUID.randomUUID();
        when(firebaseVerificador.verificar("token-real"))
                .thenReturn(new FirebaseTokenVerifier.TokenVerificado("uid-firebase", true));
        when(identidadUsuarios.resolverUsuario("uid-firebase", "token-real")).thenReturn(interno);
        when(gestion.crear(any())).thenReturn(pub);
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .header("Authorization", "Bearer token-real")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud()))
                .andExpect(status().isCreated());
        ArgumentCaptor<GestionarPublicacionesUseCase.Crear> captor =
                ArgumentCaptor.forClass(GestionarPublicacionesUseCase.Crear.class);
        verify(gestion).crear(captor.capture());
        assertEquals(interno, captor.getValue().actorUsuarioId());
        verify(identidadUsuarios).resolverUsuario("uid-firebase", "token-real");
    }

    @Test void bearerInvalidoRechazaOperacionHttp() throws Exception {
        when(firebaseVerificador.verificar("invalido"))
                .thenThrow(new com.lendup.catalogo.application.exception.TokenFirebaseInvalidoException());
        mvc.perform(post("/api/v1/catalogo/publicaciones")
                        .header("Authorization", "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gestion);
    }

    @Test void preflightCorsSoloPermiteOrigenConfigurado() throws Exception {
        mvc.perform(options("/api/v1/catalogo/publicaciones")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/catalogo/publicaciones")
                        .header("Origin", "https://origen-no-autorizado.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
