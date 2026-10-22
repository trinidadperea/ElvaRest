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

    @Enumerated(EnumType.STRING)
    private PrioridadAlerta prioridad;

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