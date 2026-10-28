package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.CaracterDesignacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.MotivoVacancia;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Concurso extends BaseEntity<Long> {

    @Column(nullable = false)
    private LocalDate fechaApertura;

    @Column(nullable = false)
    private LocalDate fechaCierre;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConcurso estadoConcurso;

    @ManyToOne
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    /** Carácter previsto del concurso; las designaciones creadas desde él lo heredan. */
    @Enumerated(EnumType.STRING)
    private CaracterDesignacion caracterDesignacion;

    /** Origen o motivo de la vacancia que cubre el concurso; las designaciones creadas desde él lo heredan. */
    private String origenVacancia;

    /**
     * Motivo de la vacancia elegido de la lista. Se guarda como varchar para poder agregar
     * motivos sin alterar un ENUM de la base; el texto final queda en {@link #origenVacancia}.
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private MotivoVacancia motivoVacancia;

    /** Docente que se reemplaza; dato informativo, sin relación con la entidad Docente. */
    @Column(length = 150)
    private String profesorReemplazante;

    /** Indica si el concurso fue generado como rellamado de otro concurso. */
    private Boolean esRellamado;

    /** Concurso del que el rellamado hereda cargo y horas. */
    private Long concursoOrigenId;

    /** Horas configuradas del concurso (totales del flujo de toma de horas). */
    private Integer horasTotales;

    /** Horas remanentes transferidas a un rellamado: el concurso origen queda sin horas disponibles. */
    private Integer horasTransferidas;

    //Esto deberia ir en service
    public boolean estaVigente() {
        LocalDate hoy = LocalDate.now();

        return estadoConcurso == EstadoConcurso.EN_PROCESO
                && !hoy.isBefore(fechaApertura)
                && !hoy.isAfter(fechaCierre);
    }
}


