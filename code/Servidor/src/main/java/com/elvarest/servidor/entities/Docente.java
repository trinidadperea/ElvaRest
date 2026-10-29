package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.TipoCargo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Docente extends Persona {

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private boolean interno;

    @Column(nullable = false)
    private String area;

    /** DOCENTE o PRECEPTOR: define a qué concursos puede postularse. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCargo tipo = TipoCargo.DOCENTE;

}
