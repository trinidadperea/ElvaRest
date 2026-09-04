package com.elvarest.servidor.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Curso extends BaseEntity<Long>{

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private String seccion;
}
