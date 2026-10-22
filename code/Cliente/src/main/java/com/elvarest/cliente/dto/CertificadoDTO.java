package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.CaracterCertificado;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class CertificadoDTO extends BaseDTO {
    /** Postulación (docente + concurso) a la que pertenece */
    private ConcursoDocenteDTO concursoDocente;
    private TipoCertificadoDTO tipoCertificado;
    private CaracterCertificado caracter;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaVencimiento;
    /** La asigna el Servidor. */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaCarga;
    /** false = reemplazado por uno posterior (historial). Lo asigna el Servidor. */
    private boolean actual;
}
