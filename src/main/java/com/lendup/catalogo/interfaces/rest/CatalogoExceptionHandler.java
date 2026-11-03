package com.lendup.catalogo.interfaces.rest;

import com.lendup.catalogo.application.exception.AccesoDenegadoException;
import com.lendup.catalogo.application.exception.AlmacenamientoImagenNoDisponibleException;
import com.lendup.catalogo.application.exception.RecursoNoEncontradoException;
import com.lendup.catalogo.domain.model.DisponibilidadSuperpuestaException;
import com.lendup.catalogo.interfaces.rest.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class CatalogoExceptionHandler {
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ApiError> prohibido(AccesoDenegadoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(DisponibilidadSuperpuestaException.class)
    public ResponseEntity<ApiError> conflicto(DisponibilidadSuperpuestaException ex) {
        return respuesta(HttpStatus.CONFLICT, "DISPONIBILIDAD_SUPERPUESTA", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(AlmacenamientoImagenNoDisponibleException.class)
    public ResponseEntity<ApiError> sinCloudinary(AlmacenamientoImagenNoDisponibleException ex) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "ALMACENAMIENTO_NO_DISPONIBLE",
                ex.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> datosInvalidos(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f ->
                errores.putIfAbsent(f.getField(), f.getDefaultMessage() == null
                        ? "valor inválido" : f.getDefaultMessage()));
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos de entrada inválidos", errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> violaciones(ConstraintViolationException ex) {
        Map<String, String> errores = new TreeMap<>();
        ex.getConstraintViolations().forEach(v ->
                errores.put(v.getPropertyPath().toString(), v.getMessage()));
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos de entrada inválidos", errores);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> solicitudInvalida(Exception ex) {
        // No revelar trazas ni valores de entrada arbitrarios en errores de parseo.
        return respuesta(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA",
                "La solicitud contiene campos o valores inválidos", Map.of());
    }

    private ResponseEntity<ApiError> respuesta(HttpStatus estado, String codigo,
            String mensaje, Map<String, String> campos) {
        return ResponseEntity.status(estado).body(ApiError.de(codigo, mensaje, campos));
    }
}
