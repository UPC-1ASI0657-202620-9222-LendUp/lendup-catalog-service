package com.lendup.catalogo.application.service;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.AlmacenamientoImagenNoDisponibleException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.application.port.in.GestionarImagenesUseCase;
import com.lendup.catalogo.application.port.out.CategoriaPersistencePort;
import com.lendup.catalogo.application.port.out.ImagenPublicacionPersistencePort;
import com.lendup.catalogo.application.port.out.ImagenStoragePort;
import com.lendup.catalogo.application.port.out.PublicacionPersistencePort;
import com.lendup.catalogo.domain.model.ImagenPublicacion;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImagenPublicacionApplicationService implements GestionarImagenesUseCase {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_IMAGENES = 8;
    private final PublicacionPersistencePort publicaciones;
    private final CategoriaPersistencePort categorias;
    private final ImagenPublicacionPersistencePort imagenes;
    private final ObjectProvider<ImagenStoragePort> almacenamientos;

    public ImagenPublicacionApplicationService(PublicacionPersistencePort publicaciones,
            CategoriaPersistencePort categorias, ImagenPublicacionPersistencePort imagenes,
            ObjectProvider<ImagenStoragePort> almacenamientos) {
        this.publicaciones=publicaciones; this.categorias=categorias;
        this.imagenes=imagenes; this.almacenamientos=almacenamientos;
    }

    @Override @Transactional
    public ImagenPublicacion subir(UUID publicacionId, UUID actorId, byte[] archivo, String mime) {
        validarArchivo(archivo,mime);
        var publicacion=publicaciones.buscarPorIdParaActualizar(publicacionId)
                .orElseThrow(()->new RecursoNoEncontradoException("Publicación",publicacionId));
        if (!publicacion.esPropietario(actorId)) throw new AccesoDenegadoException();
        var anteriores=imagenes.listarPorPublicacion(publicacionId);
        if (anteriores.size()>=MAX_IMAGENES)
            throw new IllegalArgumentException("Máximo de 8 imágenes por publicación");
        int orden=0;
        while (ocupado(anteriores,orden)) orden++;
        var almacenamiento=exigirAlmacenamiento();
        var subida=almacenamiento.subir(publicacionId,archivo);
        var nueva=new ImagenPublicacion(UUID.randomUUID(),publicacionId,
                subida.publicId(),subida.url(),orden);
        try {
            return imagenes.guardar(nueva);
        } catch (RuntimeException ex) {
            try { almacenamiento.eliminar(subida.publicId()); }
            catch (RuntimeException secundario) { ex.addSuppressed(secundario); }
            throw ex;
        }
    }

    @Override @Transactional(readOnly=true)
    public List<ImagenPublicacion> listarPublicas(UUID publicacionId) {
        var publicacion=publicaciones.buscarPorId(publicacionId)
                .orElseThrow(()->new RecursoNoEncontradoException("Publicación",publicacionId));
        if (!publicacion.estado().esVisible()
                || categorias.buscarPorId(publicacion.detalles().categoriaId())
                        .filter(c->c.activa()).isEmpty())
            throw new RecursoNoEncontradoException("Publicación",publicacionId);
        return imagenes.listarPorPublicacion(publicacionId);
    }

    @Override @Transactional
    public void eliminar(UUID publicacionId, UUID imagenId, UUID actorId) {
        var publicacion=publicaciones.buscarPorIdParaActualizar(publicacionId)
                .orElseThrow(()->new RecursoNoEncontradoException("Publicación",publicacionId));
        if (!publicacion.esPropietario(actorId)) throw new AccesoDenegadoException();
        var imagen=imagenes.buscar(publicacionId,imagenId)
                .orElseThrow(()->new RecursoNoEncontradoException("Imagen",imagenId));
        exigirAlmacenamiento().eliminar(imagen.cloudinaryPublicId());
        if (!imagenes.eliminar(publicacionId,imagenId))
            throw new RecursoNoEncontradoException("Imagen",imagenId);
    }

    private ImagenStoragePort exigirAlmacenamiento() {
        var servicio=almacenamientos.getIfAvailable();
        if(servicio==null)throw new AlmacenamientoImagenNoDisponibleException();
        return servicio;
    }
    private static boolean ocupado(List<ImagenPublicacion> lista,int n) {
        return lista.stream().anyMatch(i->i.orden()==n);
    }
    private static void validarArchivo(byte[] b,String mime) {
        if(b==null||b.length==0||b.length>MAX_BYTES)
            throw new IllegalArgumentException("Imagen vacía o superior a 5 MB");
        boolean jpg=b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;
        boolean png=b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71
                &&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10;
        boolean webp=b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'
                &&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';
        if(!(jpg&&"image/jpeg".equals(mime)||png&&"image/png".equals(mime)
                ||webp&&"image/webp".equals(mime)))
            throw new IllegalArgumentException("Solo JPEG, PNG y WebP válidos");
    }
}
