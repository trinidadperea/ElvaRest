package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.CaracterDesignacion;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.MotivoVacancia;
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

    /** Fecha de fin real; queda vacía mientras la designación sigue vigente. */
    private LocalDate fechaFinDefinitiva;

    @Column(nullable = false)
    private String origenVacancia;

    /**
     * Motivo de la vacancia elegido de la lista. Se guarda como varchar para poder agregar
     * motivos sin alterar un ENUM de la base; el texto final queda en {@link #origenVacancia}.
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private MotivoVacancia motivoVacancia;

    /**
     * Docente que se reemplaza; dato informativo heredado del concurso o cargado a mano.
     * No se relaciona con la entidad {@link Docente}.
     */
    @Column(length = 150)
    private String profesorReemplazante;

    /** Concurso del que nació la designación (nulo en las manuales o históricas). */
    private Long concursoId;

    @Enumerated(EnumType.STRING)
    private CaracterDesignacion caracterDesignacion;

    @Enumerated(EnumType.STRING)
    private EstadoDesignacion estadoDesignacion;

    @ManyToOne
    @JoinColumn(name = "docente_id")
    private Docente docente;

    @OneToMany
    @JoinColumn(name = "designacion_id")
    private List<Alerta> alertas = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "designacion_id")
    private List<Licencia> licencias = new ArrayList<>();
}
