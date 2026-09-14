package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.EstadoConcurso;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

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
}
