package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.EstadoPostulacion;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class ConcursoDocenteDTO extends BaseDTO{
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaEmision;
    private ConcursoDTO concurso;
    private DocenteDTO docente;
    private boolean ganador;

    // --- Campos del flujo de toma de horas (persistidos en el Servidor) ---
    private Integer horasElegidas;
    private EstadoPostulacion estadoPostulacion;
    private String observacion;
}
