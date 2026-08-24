package com.elvarest.gestionconcursosdocentes.domain;

import com.elvarest.gestionconcursosdocentes.domain.enums.EstadoDesignacion;
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
public class Designacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fechaInicioEfectiva;

    @Column(nullable = false)
    private LocalDate fechaFinEstimada;

    @Column(nullable = false)
    private LocalDate fechaFinDefinitiva;

    @Column(nullable = false)
    private String origenVacancia;

    @Enumerated(EnumType.STRING)
    private EstadoDesignacion estadoDesignacion;

    @OneToOne
    @JoinColumn(name = "docente_id")
    private Docente docente;

    @OneToMany
    @JoinColumn(name = "designacion_id")
    private List<Alerta> alertas = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "designacion_id")
    private List<Licencia> licencias = new ArrayList<>();
}
