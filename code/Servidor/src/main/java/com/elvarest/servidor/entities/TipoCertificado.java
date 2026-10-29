package com.elvarest.servidor.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class TipoCertificado extends BaseEntity<Long> {

    @Column(nullable = false, unique = true, updatable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    /** Si es true, no se puede aprobar el paso 2 del concurso sin un certificado actual de este tipo. */
    @Column(nullable = false)
    private boolean obligatorio;

    /** Si es true, al cargarlo se pregunta si es definitivo o provisorio (con vencimiento). Ej.: apto psicofísico. */
    @Column(nullable = false)
    private boolean permiteProvisorio;
}
