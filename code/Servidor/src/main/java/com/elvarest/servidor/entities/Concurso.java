package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.EstadoConcurso;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Concurso extends BaseEntity<Long>{

    @Column(nullable = false)
    private LocalDate fechaApertura;

    @Column(nullable = false)
    private LocalDate fechaCierre;

    private String descripcion;

    @Column(nullable = false)
    private EstadoConcurso estadoConcurso;

    @OneToMany
    @JoinColumn(name = "concurso_id")
    private List<Docente> docentes = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;
}
