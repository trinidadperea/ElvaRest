package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.PrioridadAlerta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Alerta extends BaseEntity<Long> {

    @Column(nullable = false)
    private String mensaje;
    //private String tipo;
    @Column(nullable = false)
    private LocalDate fechaEmision;

    private boolean leida;

    @Enumerated(EnumType.STRING)
    private PrioridadAlerta prioridad;
}
