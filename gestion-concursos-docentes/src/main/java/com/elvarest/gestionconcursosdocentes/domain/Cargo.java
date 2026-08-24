package com.elvarest.gestionconcursosdocentes.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int cargaHoraria;

    private String descripcion;

    @OneToOne
    @JoinColumn(name = "materia_id")
    private Materia materia;

    @OneToMany
    @JoinColumn(name = "cargo_id")
    private List<Designacion> designaciones = new ArrayList<>();
}
