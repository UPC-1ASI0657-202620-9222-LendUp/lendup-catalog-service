package com.lendup.catalogo.interfaces.rest;

import com.lendup.catalogo.application.port.in.GestionarImagenesUseCase;
import com.lendup.catalogo.config.UsuarioActual;
import com.lendup.catalogo.interfaces.rest.dto.ImagenPublicacionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Imágenes")
@RequestMapping("/api/v1/catalogo/publicaciones/{publicacionId}/imagenes")
public class ImagenPublicacionController {
    private final GestionarImagenesUseCase gestion;
    private final UsuarioActual usuario;
    public ImagenPublicacionController(GestionarImagenesUseCase gestion,UsuarioActual usuario) {
        this.gestion=gestion;this.usuario=usuario;
    }

    @GetMapping
    public List<ImagenPublicacionResponse> listar(@PathVariable UUID publicacionId) {
        return gestion.listarPublicas(publicacionId).stream()
                .map(ImagenPublicacionResponse::deDominio).toList();
    }

    @Operation(summary = "Subir fotografía", security = @SecurityRequirement(name = "firebaseBearer"))
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImagenPublicacionResponse> subir(@PathVariable UUID publicacionId,
            @RequestPart("file") MultipartFile archivo) throws IOException {
        var img=gestion.subir(publicacionId,usuario.exigirId(),archivo.getBytes(),
                archivo.getContentType());
        return ResponseEntity.created(URI.create("/api/v1/catalogo/publicaciones/"
                +publicacionId+"/imagenes/"+img.id()))
                .body(ImagenPublicacionResponse.deDominio(img));
    }

    @Operation(summary = "Eliminar fotografía", security = @SecurityRequirement(name = "firebaseBearer"))
    @DeleteMapping("/{imagenId}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID publicacionId,
            @PathVariable UUID imagenId) {
        gestion.eliminar(publicacionId,imagenId,usuario.exigirId());
        return ResponseEntity.noContent().build();
    }
}
