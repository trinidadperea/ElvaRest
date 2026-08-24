package com.elvarest.gestionconcursosdocentes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Docente extends Persona {

    private double puntaje;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private boolean interno;

    @Column(nullable = false)
    private String area;

}
