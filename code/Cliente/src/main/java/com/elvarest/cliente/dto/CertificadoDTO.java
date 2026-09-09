package com.elvarest.cliente.dto;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class CertificadoDTO extends BaseDTO{
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaEmision;
    private boolean requiereVencimiento;
    private boolean presentado;
    private DocenteDTO docente;
    private TipoCertificado tipoCertificado;
}
