package com.elvarest.gestionconcursosdocentes.domain;

import com.elvarest.gestionconcursosdocentes.domain.enums.PrioridadAlerta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mensaje;
    //private String tipo;
    @Column(nullable = false)
    private LocalDate fechaEmision;

    private boolean leida;

    @Enumerated(EnumType.STRING)
    private PrioridadAlerta prioridad;
}
