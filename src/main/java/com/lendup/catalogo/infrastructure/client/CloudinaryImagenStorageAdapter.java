package com.lendup.catalogo.infrastructure.client;

import com.cloudinary.Cloudinary;
import com.lendup.catalogo.application.exception.AlmacenamientoImagenNoDisponibleException;
import com.lendup.catalogo.application.port.out.ImagenStoragePort;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="catalogo.cloudinary",name="enabled",havingValue="true")
public class CloudinaryImagenStorageAdapter implements ImagenStoragePort {
    private final Cloudinary cloudinary;
    public CloudinaryImagenStorageAdapter(Cloudinary cloudinary) { this.cloudinary=cloudinary; }

    @Override public Almacenada subir(UUID publicacionId,byte[] contenido) {
        try {
            String id="lendup/publicaciones/"+publicacionId+"/"+UUID.randomUUID();
            Map<?,?> resultado=cloudinary.uploader().upload(contenido,
                    Map.of("public_id",id,"overwrite",false,"resource_type","image"));
            String publicId=(String)resultado.get("public_id");
            String url=(String)resultado.get("secure_url");
            if(publicId==null||publicId.isBlank()||url==null||!url.startsWith("https://"))
                throw new AlmacenamientoImagenNoDisponibleException();
            return new Almacenada(publicId,url);
        } catch(AlmacenamientoImagenNoDisponibleException ex) { throw ex; }
          catch(Exception ex) { throw new AlmacenamientoImagenNoDisponibleException(ex); }
    }

    @Override public void eliminar(String publicId) {
        try {
            Map<?,?> resultado=cloudinary.uploader().destroy(publicId,
                    Map.of("resource_type","image","invalidate",true));
            String estado=String.valueOf(resultado.get("result"));
            if(!"ok".equals(estado)&&!"not found".equals(estado))
                throw new AlmacenamientoImagenNoDisponibleException();
        } catch(AlmacenamientoImagenNoDisponibleException ex) { throw ex; }
          catch(Exception ex) { throw new AlmacenamientoImagenNoDisponibleException(ex); }
    }
}
