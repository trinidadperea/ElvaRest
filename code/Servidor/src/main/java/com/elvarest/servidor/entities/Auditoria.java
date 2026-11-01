package com.elvarest.servidor.entities;

import com.elvarest.servidor.entities.enums.AccionAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro del historial de cambios del sistema. Solo se escribe desde el aspecto de
 * auditoría y solo se lee desde su controlador: nunca se modifica ni se da de baja.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Auditoria extends BaseEntity<Long> {

    /** Fecha y hora en que ocurrió el cambio. */
    @Column(nullable = false)
    private LocalDateTime fechaHora;

    /** Login del usuario autenticado que hizo el cambio; "SISTEMA" si no hubo sesión. */
    @Column(nullable = false, length = 50)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionAuditoria accion;

    /** Módulo o entidad afectada (nombre de la clase: "Alerta", "Concurso", ...). */
    @Column(nullable = false, length = 60)
    private String entidad;

    /** Uuid público del registro afectado. */
    @Column(nullable = false, length = 36)
    private String registroUuid;

    /** Detalle breve del cambio en JSON: campos no nulos en ALTA, diff antes/después en MODIFICACION. */
    @Column(length = 1000)
    private String detalle;
}
