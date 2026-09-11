package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.PersonaDTO;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocenteDTO extends PersonaDTO {
    private double puntaje;
    private boolean activo;
    private boolean interno;
    private String area;
}
