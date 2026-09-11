package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.EstadoConcurso;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConcursoDTO extends BaseDTO{
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaApertura;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaCierre;
    private String descripcion;
    private EstadoConcurso estadoConcurso;
    private List<DocenteDTO> docentes = new ArrayList<>();
    //private CargoDTO cargo;
}
