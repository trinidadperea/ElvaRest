package com.elvarest.cliente.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CargoDTO extends BaseDTO{
    private int cargaHoraria;
    private String descripcion;
    private MateriaDTO materia;
    private List<DesignacionDTO> designaciones = new ArrayList<>();
}
