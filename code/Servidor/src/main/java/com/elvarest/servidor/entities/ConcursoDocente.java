package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ConcursoDocente extends BaseEntity<Long> {

    @Column(nullable = false)
    private LocalDate fechaEmision;

    @ManyToOne
    @JoinColumn(name = "concurso_id", nullable = false)
    private Concurso concurso;

    @ManyToOne
    @JoinColumn(name = "docente_id", nullable = false)
    private Docente docente;

    private boolean ganador;

    /** Horas tomadas por el docente en el concurso. */
    private Integer horasElegidas;

    /** Estado del ciclo de vida de la postulación (flujo de toma de horas). */
    @Enumerated(EnumType.STRING)
    private EstadoPostulacion estadoPostulacion;

    /** Resultado de la validación de certificados. */
    private Boolean certificadosRecibidos;

    /** Indica si los certificados exigen control de vencimiento. */
    private Boolean requiereVencimientoCertificados;

    /** Meses de vigencia de los certificados (si requiere vencimiento). */
    private Integer mesesVigencia;

    /** Observación del flujo: rechazo de certificados, posesión no efectiva, etc. */
    private String observacion;
}
