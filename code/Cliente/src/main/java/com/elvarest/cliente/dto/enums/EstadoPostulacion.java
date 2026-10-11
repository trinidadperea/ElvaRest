package com.elvarest.cliente.dto.enums;

/**
 * Estados del ciclo de vida de una postulación / designación dentro de un concurso.
 * Definidos según el flujo funcional del sistema:
 *
 * PENDIENTE_CERTIFICADOS        -> el docente registró la toma de horas, falta validar documentación.
 * DESIGNADO_PENDIENTE_POSESION  -> certificados aprobados, falta la toma de posesión efectiva.
 * DESIGNACION_DEFINITIVA        -> el docente asumió el cargo (designación activa).
 * CANCELADO_INEFECTIVO          -> rechazado por documentación o no se presentó a tomar posesión.
 */
public enum EstadoPostulacion {

    PENDIENTE_CERTIFICADOS("Pendiente de certificados", "status-pending-docs", false),
    DESIGNADO_PENDIENTE_POSESION("Pendiente de posesión", "status-pending-possession", false),
    DESIGNACION_DEFINITIVA("Designación definitiva", "status-definitive", false),
    CANCELADO_INEFECTIVO("Cancelado / inefectivo", "status-cancelled", true);

    private final String etiqueta;
    private final String badgeClass;
    private final boolean cancelado;

    EstadoPostulacion(String etiqueta, String badgeClass, boolean cancelado) {
        this.etiqueta = etiqueta;
        this.badgeClass = badgeClass;
        this.cancelado = cancelado;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /** Indica si la postulación está cancelada (sus horas ya no reservan cupo). */
    public boolean isCancelado() {
        return cancelado;
    }
}
