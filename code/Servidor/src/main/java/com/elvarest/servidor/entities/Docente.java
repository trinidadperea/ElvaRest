package com.elvarest.servidor.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Docente extends Persona {

    private double puntaje;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private boolean interno;

    @Column(nullable = false)
    private String area;

}
