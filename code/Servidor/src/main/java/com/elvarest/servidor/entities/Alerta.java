package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.PrioridadAlerta;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;


import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Alerta extends BaseEntity<Long> {

    @Column(nullable = false)
    private String mensaje;
    //private String tipo;
    @Column(nullable = false)
    private LocalDate fechaEmision;

    private boolean leida;

    /**
     * Fecha en que la alerta quedó resuelta (leída). La fija el Servidor cuando pasa a leída, la
     * conserva mientras siga leída y se limpia si vuelve a pendiente. Es nula en las pendientes y
     * en las leídas anteriores a esta columna (no se infiere de otra fecha).
     */
    private LocalDate fechaResolucion;

    @Enumerated(EnumType.STRING)
    private PrioridadAlerta prioridad;

    /**
     * Concurso que originó la alerta automática de "sigue en proceso". Se guarda el id plano
     * (sin relación JPA) porque la alerta no necesita navegar al concurso y así no se agrega
     * otro ciclo de serialización. Es nulo en las alertas de certificado y en las manuales.
     */
    private Long concursoId;

    /**
     * Distingue las dos alertas automáticas de un concurso: "CONCURSO_SIN_CERRAR" y
     * "CONCURSO_HORAS_SIN_CUBRIR". Se guarda como texto (no como enum nativo) para poder agregar
     * orígenes nuevos sin alterar la columna. Es nulo en las de certificado, designación y manuales.
     */
    @Column(length = 30)
    private String tipo;

    /**
     * Certificado que originó la alerta (nulo en las alertas manuales o de designaciones).
     * Se omite la postulación al serializar para cortar el ciclo
     * Designacion -> alertas -> certificado -> postulación -> concurso -> cargo -> designaciones.
     */
    @ManyToOne
    @JoinColumn(name = "certificado_id")
    @JsonIgnoreProperties({"concursoDocente"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Certificado certificado;
}