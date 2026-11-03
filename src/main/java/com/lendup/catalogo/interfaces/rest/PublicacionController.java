package com.lendup.catalogo.interfaces.rest;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.ConsultarCategoriasUseCase;
import com.lendup.catalogo.application.port.in.ConsultarPublicacionesUseCase;
import com.lendup.catalogo.application.port.in.GestionarPublicacionesUseCase;
import com.lendup.catalogo.config.UsuarioActual;
import com.lendup.catalogo.domain.model.Publicacion;
import com.lendup.catalogo.interfaces.rest.dto.CambiarEstadoRequest;
import com.lendup.catalogo.interfaces.rest.dto.PublicacionRequest;
import com.lendup.catalogo.interfaces.rest.dto.PublicacionResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Tag(name = "Publicaciones")
@RequestMapping("/api/v1/catalogo")
public class PublicacionController {
    private final GestionarPublicacionesUseCase gestion;
    private final ConsultarPublicacionesUseCase consultas;
    private final ConsultarCategoriasUseCase categorias;
    private final UsuarioActual usuario;
    private final ParchePublicacion parche;

    public PublicacionController(GestionarPublicacionesUseCase gestion,
            ConsultarPublicacionesUseCase consultas, ConsultarCategoriasUseCase categorias,
            UsuarioActual usuario, ParchePublicacion parche) {
        this.gestion = gestion;
        this.consultas = consultas;
        this.categorias = categorias;
        this.usuario = usuario;
        this.parche = parche;
    }

    @GetMapping("/publicaciones")
    public List<PublicacionResponse> listar() {
        return consultas.listarActivas().stream().map(PublicacionResponse::deDominio).toList();
    }

    /** Detalle público: no revelar publicaciones pausadas ni de categorías desactivadas. */
    @GetMapping("/publicaciones/{id}")
    public PublicacionResponse obtener(@PathVariable UUID id) {
        Publicacion pub = consultas.obtener(id);
        if (!pub.estado().esVisible() || !categorias.obtener(pub.detalles().categoriaId()).activa()) {
            throw new RecursoNoEncontradoException("Publicación", id);
        }
        return PublicacionResponse.deDominio(pub);
    }

    @Operation(summary = "Mis publicaciones", security = @SecurityRequirement(name = "firebaseBearer"))
    @GetMapping("/mis-publicaciones")
    public List<PublicacionResponse> mias() {
        return consultas.listarMisPublicaciones(usuario.exigirId()).stream()
                .map(PublicacionResponse::deDominio).toList();
    }

    @Operation(summary = "Crear publicación", security = @SecurityRequirement(name = "firebaseBearer"))
    @PostMapping("/publicaciones")
    public ResponseEntity<PublicacionResponse> crear(@Valid @RequestBody PublicacionRequest solicitud) {
        var creada = gestion.crear(new GestionarPublicacionesUseCase.Crear(usuario.exigirId(),
                solicitud.detalles().aDominio(), solicitud.condiciones().aDominio()));
        return ResponseEntity.created(URI.create("/api/v1/catalogo/publicaciones/" + creada.id()))
                .body(PublicacionResponse.deDominio(creada));
    }

    @Operation(summary = "Actualizar publicación", security = @SecurityRequirement(name = "firebaseBearer"))
    @PutMapping("/publicaciones/{id}")
    public PublicacionResponse actualizar(@PathVariable UUID id,
            @Valid @RequestBody PublicacionRequest solicitud) {
        return PublicacionResponse.deDominio(gestion.actualizar(
                new GestionarPublicacionesUseCase.Actualizar(id, usuario.exigirId(),
                        solicitud.detalles().aDominio(), solicitud.condiciones().aDominio())));
    }

    @Operation(summary = "Modificar parcialmente", security = @SecurityRequirement(name = "firebaseBearer"))
    @PatchMapping("/publicaciones/{id}")
    public PublicacionResponse actualizarParcial(@PathVariable UUID id,
            @RequestBody JsonNode cambios) {
        UUID actor = usuario.exigirId();
        Publicacion actual = consultas.obtener(id);
        if (!actual.esPropietario(actor)) {
            throw new AccesoDenegadoException();
        }
        var combinado = parche.aplicar(actual, cambios);
        return PublicacionResponse.deDominio(gestion.actualizar(
                new GestionarPublicacionesUseCase.Actualizar(id, actor,
                        combinado.detalles().aDominio(), combinado.condiciones().aDominio())));
    }

    @Operation(summary = "Cambiar estado", security = @SecurityRequirement(name = "firebaseBearer"))
    @PatchMapping("/publicaciones/{id}/estado")
    public PublicacionResponse cambiarEstado(@PathVariable UUID id,
            @Valid @RequestBody CambiarEstadoRequest solicitud) {
        return PublicacionResponse.deDominio(gestion.cambiarEstado(
                new GestionarPublicacionesUseCase.CambiarEstado(
                        id, usuario.exigirId(), solicitud.estado())));
    }
}
