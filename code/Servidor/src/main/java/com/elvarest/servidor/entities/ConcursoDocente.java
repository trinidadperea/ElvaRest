package com.elvarest.servidor.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ConcursoDocente extends BaseEntity<Long> {

    @Column(nullable = false)
    private LocalDate fechaEmision;

    @ManyToOne
    @JoinColumn(name = "concurso_id", nullable = false)
    private Concurso concurso;

    @ManyToOne
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    private boolean ganador;
}
