package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.CaracterCertificado;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Certificado presentado por un docente para una postulación puntual (docente + concurso).
 * Se carga de cero en cada concurso ganado. Nunca se edita: para renovarlo se carga uno nuevo
 * y el anterior queda en el historial con actual = false.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Certificado extends BaseEntity<Long> {

    /** Postulación (docente + concurso) a la que pertenece el certificado. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "concurso_docente_id", nullable = false)
    private ConcursoDocente concursoDocente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tipo_certificado_id", nullable = false)
    private TipoCertificado tipoCertificado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaracterCertificado caracter;

    /** Solo para certificados provisorios. */
    private LocalDate fechaVencimiento;

    /** Fecha en que se registró en el sistema (la asigna el servidor). Ordena el historial. */
    @Column(nullable = false)
    private LocalDate fechaCarga;

    /** true: es el certificado vigente de su tipo en la postulación; false: fue reemplazado por uno posterior. */
    @Column(nullable = false)
    private boolean actual;
}
