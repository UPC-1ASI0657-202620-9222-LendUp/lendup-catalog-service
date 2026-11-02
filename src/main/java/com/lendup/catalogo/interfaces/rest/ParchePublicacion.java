package com.lendup.catalogo.interfaces.rest;

import com.lendup.catalogo.domain.model.Publicacion;
import com.lendup.catalogo.interfaces.rest.dto.PublicacionRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fusiona sólo campos presentes; conserva omitidos. "garantiaMonetaria": null
 * elimina la garantía. No permite nuevas propiedades ni cambios de propietario/ID.
 */
@Component
public class ParchePublicacion {
    private final JsonMapper mapper;
    private final Validator validator;

    public ParchePublicacion(JsonMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public PublicacionRequest aplicar(Publicacion actual, JsonNode cambios) {
        if (cambios == null || !cambios.isObject() || cambios.isEmpty()) {
            throw new IllegalArgumentException("El PATCH requiere un objeto JSON no vacío");
        }
        ObjectNode base = mapper.valueToTree(PublicacionRequest.deDominio(actual));
        fusionar(base, cambios);
        try {
            PublicacionRequest combinado = mapper.treeToValue(base, PublicacionRequest.class);
            var violaciones = validator.validate(combinado);
            if (!violaciones.isEmpty()) {
                throw new ConstraintViolationException(violaciones);
            }
            return combinado;
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Datos inválidos en PATCH", ex);
        }
    }

    private void fusionar(ObjectNode base, JsonNode cambios) {
        for (Map.Entry<String, JsonNode> campo : cambios.properties()) {
            String nombre = campo.getKey();
            if (!base.has(nombre)) {
                throw new IllegalArgumentException("Campo no permitido en PATCH: " + nombre);
            }
            JsonNode reemplazo = campo.getValue();
            JsonNode anterior = base.get(nombre);
            if (reemplazo.isObject()) {
                if (!anterior.isObject()) {
                    throw new IllegalArgumentException("Objeto inesperado para: " + nombre);
                }
                fusionar((ObjectNode) anterior, reemplazo);
            } else {
                base.set(nombre, reemplazo);
            }
        }
    }
}
