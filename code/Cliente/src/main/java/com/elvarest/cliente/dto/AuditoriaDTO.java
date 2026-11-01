package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.AccionAuditoria;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Registro del historial de cambios, tal como lo expone la API del Servidor.
 * Es un DTO de solo lectura: se deserializa desde la API y se muestra, pero nunca se
 * enlaza desde un formulario ni se vuelve a enviar al Servidor. El id numérico no viaja
 * en la API: el registro se identifica únicamente por su uuid (BaseDTO).
 */
@Getter
@Setter
public class AuditoriaDTO extends BaseDTO {

    /** Fecha y hora en que ocurrió el cambio. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fechaHora;

    /** Login del usuario que hizo el cambio; "SISTEMA" si no hubo sesión. */
    private String usuario;

    private AccionAuditoria accion;

    /** Módulo o entidad afectada (nombre de la clase: "Alerta", "Concurso", ...). */
    private String entidad;

    /** UUID público del registro afectado. */
    private String registroUuid;

    /** Detalle breve del cambio en JSON (campos en un alta, antes/después en una modificación). */
    private String detalle;
}