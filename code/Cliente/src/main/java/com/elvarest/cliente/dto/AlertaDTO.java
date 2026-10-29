package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.PrioridadAlerta;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class AlertaDTO extends BaseDTO{
    private String mensaje;
    //private String tipo;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaEmision;
    private boolean leida;
    private PrioridadAlerta prioridad;
    /** Fecha en que la alerta quedó resuelta; la registra el Servidor. Nula si está pendiente. */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaResolucion;
    /** Certificado que originó la alerta (nulo en las alertas manuales). Llega sin la postulación. */
    private CertificadoDTO certificado;
    /** Concurso que originó la alerta automática (id plano); nulo en las de certificado y designación. */
    private Long concursoId;
}
