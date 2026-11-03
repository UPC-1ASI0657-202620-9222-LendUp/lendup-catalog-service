package com.lendup.catalogo.interfaces.rest;

import com.lendup.catalogo.application.port.in.GestionarDisponibilidadUseCase;
import com.lendup.catalogo.config.UsuarioActual;
import com.lendup.catalogo.interfaces.rest.dto.DisponibilidadRequest;
import com.lendup.catalogo.interfaces.rest.dto.DisponibilidadResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Disponibilidad")
@SecurityRequirement(name = "firebaseBearer")
@RequestMapping("/api/v1/catalogo/publicaciones/{publicacionId}/disponibilidades")
public class DisponibilidadController {
    private final GestionarDisponibilidadUseCase disponibilidad;
    private final UsuarioActual usuario;

    public DisponibilidadController(GestionarDisponibilidadUseCase disponibilidad,
            UsuarioActual usuario) {
        this.disponibilidad = disponibilidad;
        this.usuario = usuario;
    }

    @GetMapping
    public List<DisponibilidadResponse> listar(@PathVariable UUID publicacionId) {
        return disponibilidad.listar(publicacionId, usuario.exigirId()).stream()
                .map(DisponibilidadResponse::deDominio).toList();
    }

    @PostMapping
    public ResponseEntity<DisponibilidadResponse> agregar(@PathVariable UUID publicacionId,
            @Valid @RequestBody DisponibilidadRequest solicitud) {
        var nueva = disponibilidad.agregar(new GestionarDisponibilidadUseCase.Agregar(
                publicacionId, usuario.exigirId(), solicitud.aDominio()));
        return ResponseEntity.created(URI.create("/api/v1/catalogo/publicaciones/"
                + publicacionId + "/disponibilidades/" + nueva.id()))
                .body(DisponibilidadResponse.deDominio(nueva));
    }

    @PutMapping("/{id}")
    public DisponibilidadResponse actualizar(@PathVariable UUID publicacionId,
            @PathVariable UUID id, @Valid @RequestBody DisponibilidadRequest solicitud) {
        return DisponibilidadResponse.deDominio(disponibilidad.actualizar(
                new GestionarDisponibilidadUseCase.Actualizar(
                        publicacionId, id, usuario.exigirId(), solicitud.aDominio())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID publicacionId, @PathVariable UUID id) {
        disponibilidad.eliminar(new GestionarDisponibilidadUseCase.Eliminar(
                publicacionId, id, usuario.exigirId()));
        return ResponseEntity.noContent().build();
    }
}
