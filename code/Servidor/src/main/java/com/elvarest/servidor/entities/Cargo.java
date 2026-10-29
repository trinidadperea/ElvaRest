package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.TipoCargo;
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
public class Cargo extends BaseEntity<Long> {

    @Column(nullable = false)
    private int cargaHoraria;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCargo tipoCargo = TipoCargo.DOCENTE;

    @OneToOne
    @JoinColumn(name = "materia_id")
    private Materia materia;

    @OneToMany
    @JoinColumn(name = "cargo_id")
    private List<Designacion> designaciones = new ArrayList<>();
}
