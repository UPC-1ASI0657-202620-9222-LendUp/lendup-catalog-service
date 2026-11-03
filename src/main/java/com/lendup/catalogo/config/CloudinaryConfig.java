package com.lendup.catalogo.config;

import com.cloudinary.Cloudinary;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {
    @Bean
    @ConditionalOnProperty(prefix="catalogo.cloudinary",name="enabled",havingValue="true")
    Cloudinary cloudinary(@Value("${cloudinary.cloud-name:}") String nombre,
            @Value("${cloudinary.api-key:}") String clave,
            @Value("${cloudinary.api-secret:}") String secreto) {
        if (nombre.isBlank() || clave.isBlank() || secreto.isBlank())
            throw new IllegalStateException("Configura las variables CLOUDINARY_CLOUD_NAME, "
                    + "CLOUDINARY_API_KEY y CLOUDINARY_API_SECRET");
        return new Cloudinary(Map.of("cloud_name",nombre,"api_key",clave,
                "api_secret",secreto,"secure",true));
    }
}
