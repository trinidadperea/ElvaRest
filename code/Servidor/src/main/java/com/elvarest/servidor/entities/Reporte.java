package com.elvarest.servidor.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Reporte extends BaseEntity<Long> {

    @Column(nullable = false)
    private String titular;

    @Column(nullable = false)
    private LocalDate fecha;
}