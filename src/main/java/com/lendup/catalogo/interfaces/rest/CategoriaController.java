package com.lendup.catalogo.interfaces.rest;

import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.ConsultarCategoriasUseCase;
import com.lendup.catalogo.interfaces.rest.dto.CategoriaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Categorías")
@RequestMapping("/api/v1/catalogo/categorias")
public class CategoriaController {
    private final ConsultarCategoriasUseCase categorias;

    public CategoriaController(ConsultarCategoriasUseCase categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    public List<CategoriaResponse> listar() {
        return categorias.listarActivas().stream().map(CategoriaResponse::deDominio).toList();
    }

    @GetMapping("/{id}")
    public CategoriaResponse obtener(@PathVariable UUID id) {
        var categoria = categorias.obtener(id);
        if (!categoria.activa()) {
            throw new RecursoNoEncontradoException("Categoría", id);
        }
        return CategoriaResponse.deDominio(categoria);
    }
}
