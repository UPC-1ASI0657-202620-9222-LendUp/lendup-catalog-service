package com.lendup.catalogo.infrastructure.persistence.entity;

import com.lendup.catalogo.domain.model.CondicionesPublicacion;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Embeddable
public class CondicionesPublicacionEmbeddable {
    @Column(name="tarifa_diaria", nullable=false, precision=12, scale=2)
    private BigDecimal tarifaDiaria;
    @Column(name="garantia_monetaria", precision=12, scale=2)
    private BigDecimal garantiaMonetaria;
    @Column(name="moneda", nullable=false, length=3, columnDefinition="char(3)")
    private String moneda;
    @Column(name="lugar_intercambio", nullable=false, length=250)
    private String lugarIntercambio;
    @Column(name="condiciones_uso", nullable=false, columnDefinition="text")
    private String condicionesUso;
    @Column(name="condiciones_entrega", nullable=false, columnDefinition="text")
    private String condicionesEntrega;
    @Column(name="condiciones_devolucion", nullable=false, columnDefinition="text")
    private String condicionesDevolucion;
    @Column(name="condiciones_cancelacion", nullable=false, columnDefinition="text")
    private String condicionesCancelacion;

    protected CondicionesPublicacionEmbeddable() {}
    public CondicionesPublicacionEmbeddable(CondicionesPublicacion c) {
        tarifaDiaria=c.tarifaDiaria(); garantiaMonetaria=c.garantiaMonetaria();
        moneda=c.moneda(); lugarIntercambio=c.lugarIntercambio();
        condicionesUso=c.condicionesUso(); condicionesEntrega=c.condicionesEntrega();
        condicionesDevolucion=c.condicionesDevolucion(); condicionesCancelacion=c.condicionesCancelacion();
    }
    public CondicionesPublicacion aDominio() {
        return new CondicionesPublicacion(tarifaDiaria, garantiaMonetaria, moneda, lugarIntercambio,
                condicionesUso, condicionesEntrega, condicionesDevolucion, condicionesCancelacion);
    }
}
