package com.lendup.catalogo.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoriaApplicationServiceTest {
    private final CategoriaPersistencePort repo = mock(CategoriaPersistencePort.class);
    private final CategoriaApplicationService service = new CategoriaApplicationService(repo);

    @Test void recuperaCategoriaYListaActivas() {
        var categoria = new Categoria(UUID.randomUUID(), "TEC", "Tecnología", true);
        when(repo.buscarPorId(categoria.id())).thenReturn(Optional.of(categoria));
        when(repo.listarActivas()).thenReturn(List.of(categoria));
        assertEquals(categoria, service.obtener(categoria.id()));
        assertEquals(List.of(categoria), service.listarActivas());
    }

    @Test void informaCategoriaInexistente() {
        UUID id = UUID.randomUUID();
        when(repo.buscarPorId(id)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.obtener(id));
    }
}
