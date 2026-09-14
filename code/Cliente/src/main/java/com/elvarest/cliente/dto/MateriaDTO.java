package com.elvarest.cliente.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MateriaDTO extends BaseDTO{
    private String nombre;
    private String area;
    private CursoDTO curso;
}
