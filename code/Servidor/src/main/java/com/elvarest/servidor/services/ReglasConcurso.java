package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.entities.enums.TipoCargo;

import java.util.List;
import java.util.Objects;

/**
 * Reglas de estado y horas del flujo de concursos (§2 y §3.10 de las reglas de negocio).
 * Mismo criterio que FlujoConcursoService del Cliente.
 */
public final class ReglasConcurso {

    private ReglasConcurso() {
    }

    /** Estado persistido; los registros históricos sin estado se derivan del ganador (§9). */
    public static EstadoPostulacion estadoEfectivo(ConcursoDocente postulacion) {
        if (postulacion.getEstadoPostulacion() != null) {
            return postulacion.getEstadoPostulacion();
        }
        return postulacion.isGanador()
                ? EstadoPostulacion.DESIGNACION_DEFINITIVA
                : EstadoPostulacion.PENDIENTE_CERTIFICADOS;
    }

    /** Tipo del cargo del concurso; los registros sin tipo (previos a la migración) son DOCENTE. */
    public static TipoCargo tipoCargo(Concurso concurso) {
        if (concurso.getCargo() == null || concurso.getCargo().getTipoCargo() == null) {
            return TipoCargo.DOCENTE;
        }
        return concurso.getCargo().getTipoCargo();
    }

    /** Un concurso de preceptor admite una sola persona, que toma todas las horas. */
    public static boolean esDePreceptor(Concurso concurso) {
        return tipoCargo(concurso) == TipoCargo.PRECEPTOR;
    }

    public static boolean esCancelada(ConcursoDocente postulacion) {
        return estadoEfectivo(postulacion) == EstadoPostulacion.CANCELADO_INEFECTIVO;
    }

    /** Horas totales persistidas o, como respaldo, la carga horaria del cargo; null si no hay ninguna. */
    public static Integer horasTotales(Concurso concurso) {
        if (concurso.getHorasTotales() != null) {
            return concurso.getHorasTotales();
        }
        if (concurso.getCargo() != null && concurso.getCargo().getCargaHoraria() > 0) {
            return concurso.getCargo().getCargaHoraria();
        }
        return null;
    }

    /** Suma de horas de las postulaciones vigentes (excluye eliminadas, canceladas y sin horas). */
    public static int horasAsignadas(List<ConcursoDocente> postulaciones) {
        return postulaciones.stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .filter(postulacion -> !esCancelada(postulacion))
                .map(ConcursoDocente::getHorasElegidas)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    /** Horas disponibles: total − asignadas − transferidas, nunca menor a 0; null si el concurso no tiene horas. */
    public static Integer horasDisponibles(Concurso concurso, List<ConcursoDocente> postulaciones) {
        Integer total = horasTotales(concurso);
        if (total == null) {
            return null;
        }
        int transferidas = concurso.getHorasTransferidas() != null ? concurso.getHorasTransferidas() : 0;
        return Math.max(0, total - horasAsignadas(postulaciones) - transferidas);
    }

    /** Texto para mostrar al usuario (mismas etiquetas que el Cliente). */
    public static String etiqueta(EstadoPostulacion estado) {
        return switch (estado) {
            case PENDIENTE_CERTIFICADOS -> "Pendiente de certificados";
            case DESIGNADO_PENDIENTE_POSESION -> "Pendiente de posesión";
            case DESIGNACION_DEFINITIVA -> "Designación definitiva";
            case CANCELADO_INEFECTIVO -> "Cancelado / inefectivo";
        };
    }

    /** Descripción del concurso o "#id" cuando falta (registros antiguos), para textos al usuario. */
    public static String descripcion(Concurso concurso) {
        if (concurso.getDescripcion() != null && !concurso.getDescripcion().isBlank()) {
            return concurso.getDescripcion();
        }
        return "#" + concurso.getId();
    }

    /**
     * Cierre automático: el concurso no está cerrado, todas las postulaciones vigentes quedaron con
     * designación definitiva y no quedan horas disponibles (ni sin asignar ni transferidas).
     */
    public static boolean esListoParaCierre(Concurso concurso, List<ConcursoDocente> postulaciones) {
        if (concurso.getEstadoConcurso() == EstadoConcurso.FINALIZADO
                || postulaciones == null || postulaciones.isEmpty()) {
            return false;
        }
        List<ConcursoDocente> vigentes = postulaciones.stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .filter(postulacion -> !esCancelada(postulacion))
                .toList();
        if (vigentes.isEmpty()) {
            return false;
        }
        boolean todosDefinitivos = vigentes.stream()
                .allMatch(postulacion -> estadoEfectivo(postulacion) == EstadoPostulacion.DESIGNACION_DEFINITIVA);
        Integer disponibles = horasDisponibles(concurso, postulaciones);
        return todosDefinitivos && disponibles != null && disponibles <= 0;
    }
}
