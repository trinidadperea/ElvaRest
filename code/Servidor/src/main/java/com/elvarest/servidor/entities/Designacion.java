package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.EstadoDesignacion;
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
public class Designacion extends BaseEntity<Long>{

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
