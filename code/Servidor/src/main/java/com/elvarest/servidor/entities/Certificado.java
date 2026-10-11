package com.elvarest.servidor.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Certificado extends BaseEntity<Long> {

    @Column(nullable = false)
    private LocalDate fechaEmision;

    @Column(nullable = false)
    private boolean requiereVencimiento;

    private LocalDate fechaVencimiento;

    private boolean presentado;

    @ManyToOne
    @JoinColumn(name = "docente_id")
    private Docente docente;

    @ManyToOne
    @JoinColumn(name = "tipo_certificado_id")
    private TipoCertificado tipoCertificado;

    public boolean esVigente() {
        if (!requiereVencimiento) {
            return true;
        }
        return fechaVencimiento != null && !fechaVencimiento.isBefore(LocalDate.now());
    }
}
