package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.CaracterDesignacion;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DesignacionDTO extends BaseDTO{
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaInicioEfectiva;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFinEstimada;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFinDefinitiva;
    private String origenVacancia;
    private CaracterDesignacion caracterDesignacion;
    private EstadoDesignacion estadoDesignacion;
    private DocenteDTO docente;
    private List<AlertaDTO> alertas = new ArrayList<>();
    private List<LicenciaDTO> licencias = new ArrayList<>();

}
