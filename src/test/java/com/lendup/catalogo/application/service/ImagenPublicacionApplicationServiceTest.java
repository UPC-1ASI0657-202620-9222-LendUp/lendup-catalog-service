package com.lendup.catalogo.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.AlmacenamientoImagenNoDisponibleException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.application.port.out.ImagenPublicacionPersistencePort;
import com.lendup.catalogo.application.port.out.ImagenStoragePort;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.Categoria;
import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import com.lendup.catalogo.domain.model.DetallesPublicacion;
import com.lendup.catalogo.domain.model.EstadoPublicacion;
import com.lendup.catalogo.domain.model.ImagenPublicacion;
import com.lendup.catalogo.domain.model.Publicacion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ImagenPublicacionApplicationServiceTest {
    @Mock private PublicacionPersistencePort publicaciones;
    @Mock private CategoriaPersistencePort categorias;
    @Mock private ImagenPublicacionPersistencePort imagenes;
    @Mock private ObjectProvider<ImagenStoragePort> proveedores;
    @Mock private ImagenStoragePort cloudinary;
    private ImagenPublicacionApplicationService servicio;
    private final UUID actor=UUID.randomUUID();
    private final UUID categoriaId=UUID.randomUUID();
    private final UUID publicacionId=UUID.randomUUID();

    @BeforeEach void preparar() {
        servicio=new ImagenPublicacionApplicationService(publicaciones,categorias,imagenes,proveedores);
    }

    private Publicacion publicacion() {
        return Publicacion.nueva(publicacionId,actor,
                new DetallesPublicacion(categoriaId,"Cámara","Para la universidad",
                        "Buen estado","UPC","Monterrico","Campus",null,null),
                new CondicionesPublicacion(new BigDecimal("20.00"),null,"PEN",
                        "Campus","Uso responsable","En mano","En mano","Avisar"));
    }

    private byte[] jpeg() {
        return new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0,0,0};
    }

    @Test void subidaRegistraUrlYOrdenSinExponerSecretos() {
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        when(imagenes.listarPorPublicacion(publicacionId)).thenReturn(List.of());
        when(proveedores.getIfAvailable()).thenReturn(cloudinary);
        when(cloudinary.subir(eq(publicacionId),any(byte[].class)))
                .thenReturn(new ImagenStoragePort.Almacenada(
                        "lendup/publicaciones/uno","https://res.cloudinary.com/test/image/upload/a.jpg"));
        when(imagenes.guardar(any())).thenAnswer(inv->inv.getArgument(0));
        var guardada=servicio.subir(publicacionId,actor,jpeg(),"image/jpeg");
        assertEquals(0,guardada.orden());
        assertEquals("lendup/publicaciones/uno",guardada.cloudinaryPublicId());
        assertTrue(guardada.url().startsWith("https://"));
        verify(imagenes).guardar(guardada);
    }

    @Test void soloPropietarioPuedeSubir() {
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        assertThrows(AccesoDenegadoException.class,
                ()->servicio.subir(publicacionId,UUID.randomUUID(),jpeg(),"image/jpeg"));
        verifyNoInteractions(imagenes,cloudinary);
    }

    @Test void rechazaArchivosFalsosYExcesivosAntesDeSubir() {
        assertThrows(IllegalArgumentException.class,
                ()->servicio.subir(publicacionId,actor,"texto".getBytes(),"image/jpeg"));
        assertThrows(IllegalArgumentException.class,
                ()->servicio.subir(publicacionId,actor,new byte[5*1024*1024+1],"image/png"));
        verifyNoInteractions(publicaciones,cloudinary);
    }

    @Test void maximoOchoImagenesSinSubirArchivoNuevo() {
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        var lista=java.util.stream.IntStream.range(0,8)
                .mapToObj(n->new ImagenPublicacion(UUID.randomUUID(),publicacionId,
                        "cloud/"+n,"https://example.com/"+n,n)).toList();
        when(imagenes.listarPorPublicacion(publicacionId)).thenReturn(lista);
        assertThrows(IllegalArgumentException.class,
                ()->servicio.subir(publicacionId,actor,jpeg(),"image/jpeg"));
        verifyNoInteractions(cloudinary);
    }

    @Test void sinConfigCloudinaryDevuelve503Logico() {
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        when(imagenes.listarPorPublicacion(publicacionId)).thenReturn(List.of());
        assertThrows(AlmacenamientoImagenNoDisponibleException.class,
                ()->servicio.subir(publicacionId,actor,jpeg(),"image/jpeg"));
    }

    @Test void compensaSubidaSiFallaPersistencia() {
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        when(imagenes.listarPorPublicacion(publicacionId)).thenReturn(List.of());
        when(proveedores.getIfAvailable()).thenReturn(cloudinary);
        when(cloudinary.subir(eq(publicacionId),any(byte[].class)))
                .thenReturn(new ImagenStoragePort.Almacenada("cloud/uno","https://example.com/1"));
        when(imagenes.guardar(any())).thenThrow(new IllegalStateException("BD"));
        assertThrows(IllegalStateException.class,
                ()->servicio.subir(publicacionId,actor,jpeg(),"image/jpeg"));
        verify(cloudinary).eliminar("cloud/uno");
    }

    @Test void imagenesDePublicacionPausadaNoSonPublicas() {
        var pub=publicacion();
        pub.cambiarEstado(EstadoPublicacion.PAUSADA);
        when(publicaciones.buscarPorId(publicacionId)).thenReturn(Optional.of(pub));
        assertThrows(RecursoNoEncontradoException.class,
                ()->servicio.listarPublicas(publicacionId));
        verifyNoInteractions(imagenes);
    }

    @Test void devuelveImagenesDePublicacionConCategoriaActiva() {
        when(publicaciones.buscarPorId(publicacionId)).thenReturn(Optional.of(publicacion()));
        when(categorias.buscarPorId(categoriaId))
                .thenReturn(Optional.of(new Categoria(categoriaId,"FOTO","Fotografía",true)));
        when(imagenes.listarPorPublicacion(publicacionId)).thenReturn(List.of());
        assertTrue(servicio.listarPublicas(publicacionId).isEmpty());
    }

    @Test void soloElPropietarioPuedeEliminar() {
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        assertThrows(AccesoDenegadoException.class,
                ()->servicio.eliminar(publicacionId,UUID.randomUUID(),UUID.randomUUID()));
        verifyNoInteractions(imagenes,cloudinary);
    }

    @Test void eliminacionRemotaAntesDeBorrarMetadatos() {
        var fotoId=UUID.randomUUID();
        var foto=new ImagenPublicacion(fotoId,publicacionId,"cloud/uno","https://example.com/1",0);
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        when(imagenes.buscar(publicacionId,fotoId)).thenReturn(Optional.of(foto));
        when(proveedores.getIfAvailable()).thenReturn(cloudinary);
        when(imagenes.eliminar(publicacionId,fotoId)).thenReturn(true);
        servicio.eliminar(publicacionId,fotoId,actor);
        var orden=inOrder(cloudinary,imagenes);
        orden.verify(cloudinary).eliminar("cloud/uno");
        orden.verify(imagenes).eliminar(publicacionId,fotoId);
    }

    @Test void errorCloudinaryConservaImagenEnBD() {
        var fotoId=UUID.randomUUID();
        when(publicaciones.buscarPorIdParaActualizar(publicacionId))
                .thenReturn(Optional.of(publicacion()));
        when(imagenes.buscar(publicacionId,fotoId))
                .thenReturn(Optional.of(new ImagenPublicacion(fotoId,publicacionId,
                        "cloud/uno","https://example.com/1",0)));
        when(proveedores.getIfAvailable()).thenReturn(cloudinary);
        doThrow(new AlmacenamientoImagenNoDisponibleException())
                .when(cloudinary).eliminar("cloud/uno");
        assertThrows(AlmacenamientoImagenNoDisponibleException.class,
                ()->servicio.eliminar(publicacionId,fotoId,actor));
        verify(imagenes,never()).eliminar(any(),any());
    }
}
