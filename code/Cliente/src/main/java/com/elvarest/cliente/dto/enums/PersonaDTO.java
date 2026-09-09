package com.elvarest.cliente.dto.enums;

import com.elvarest.cliente.dto.BaseDTO;
import jakarta.persistence.Column;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class PersonaDTO extends BaseDTO {
    private String nombre;
    private String apellido;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaNacimiento;

    //@Enumerated(EnumType.STRING)
    //private TipoDocumento tipoDocumento;

    private String numeroDocumento;
    private String telefono;
    private String correoElectronico;
}
