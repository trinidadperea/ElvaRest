package com.elvarest.cliente.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TipoCertificadoDTO extends BaseDTO{
    private String codigo;
    private String nombre;
    private int mesesVigencia;
}
