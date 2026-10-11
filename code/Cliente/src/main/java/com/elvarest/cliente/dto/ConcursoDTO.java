package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.CaracterDesignacion;
import com.elvarest.cliente.dto.enums.EstadoConcurso;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ConcursoDTO extends BaseDTO{
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaApertura;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaCierre;
    private String descripcion;
    private EstadoConcurso estadoConcurso;
    private CargoDTO cargo;

    /** Carácter previsto del concurso; las designaciones creadas desde él lo heredan. */
    private CaracterDesignacion caracterDesignacion;

    /** Origen o motivo de la vacancia; las designaciones creadas desde el concurso lo heredan. */
    private String origenVacancia;

    // --- Campos del flujo de toma de horas (persistidos en el Servidor) ---
    private Integer horasTotales;

    /** Horas remanentes transferidas a un rellamado; el concurso origen queda sin horas disponibles. */
    private Integer horasTransferidas;

    /** Fecha y hora del concurso (la fecha también se persiste en fechaCierre). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fechaConcurso;

    /** Indica si el concurso fue generado como rellamado de otro. */
    private Boolean esRellamado;
    private Long concursoOrigenId;

    /**
     * Solo UI: el concurso se está creando desde "Abrir concurso para este cargo",
     * por lo que el cargo viene fijado y no debe poder modificarse en el formulario.
     * Se inicializa en false: el formulario lo evalúa como booleano (operador {@code and})
     * y no tolera null en la alta directa de "Nuevo concurso".
     */
    private Boolean cargoFijado = false;
}
