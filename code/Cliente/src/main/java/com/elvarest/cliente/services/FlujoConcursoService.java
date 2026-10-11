package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.enums.EstadoConcurso;
import com.elvarest.cliente.dto.enums.EstadoPostulacion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Reglas del flujo de concursos compartidas entre controladores (detalle de concurso,
 * dashboard, reportes): estado efectivo de una postulación, horas totales / asignadas /
 * disponibles y condiciones de cierre.
 *
 * Los datos del flujo (estado, horas, transferencias) se leen del DTO persistido en el
 * Servidor; los registros históricos sin dato usan una derivación de respaldo.
 */
@Service
public class FlujoConcursoService {

    private final ConcursoDocenteService concursoDocenteService;

    public FlujoConcursoService(ConcursoDocenteService concursoDocenteService) {
        this.concursoDocenteService = concursoDocenteService;
    }

    /** Postulaciones (toma de horas) de un concurso puntual. */
    public List<ConcursoDocenteDTO> postulantesDelConcurso(Long concursoId) {
        return concursoDocenteService.listarActivos().stream()
                .filter(postulacion -> postulacion.getConcurso() != null
                        && Objects.equals(postulacion.getConcurso().getId(), concursoId))
                .toList();
    }

    /** Filtra, desde una lista ya cargada, las postulaciones de un concurso (evita llamadas repetidas a la API). */
    public List<ConcursoDocenteDTO> postulantesDe(List<ConcursoDocenteDTO> postulaciones, Long concursoId) {
        return postulaciones.stream()
                .filter(postulacion -> postulacion.getConcurso() != null
                        && Objects.equals(postulacion.getConcurso().getId(), concursoId))
                .toList();
    }

    /** Estado efectivo de una postulación: DTO persistido -> derivado del ganador (registros históricos). */
    public EstadoPostulacion estadoEfectivo(ConcursoDocenteDTO postulacion) {
        if (postulacion.getEstadoPostulacion() != null) {
            return postulacion.getEstadoPostulacion();
        }
        return postulacion.isGanador() ? EstadoPostulacion.DESIGNACION_DEFINITIVA : EstadoPostulacion.PENDIENTE_CERTIFICADOS;
    }

    /** Horas efectivas de una postulación (persistidas en el Servidor). */
    public Integer horasEfectivas(ConcursoDocenteDTO postulacion) {
        return postulacion.getHorasElegidas();
    }

    /** Horas totales del concurso: DTO persistido -> carga horaria del cargo (respaldo). */
    public Integer horasTotalesDe(ConcursoDTO concurso) {
        if (concurso.getHorasTotales() != null) {
            return concurso.getHorasTotales();
        }
        if (concurso.getCargo() != null && concurso.getCargo().getCargaHoraria() > 0) {
            return concurso.getCargo().getCargaHoraria();
        }
        return null;
    }

    /** Suma de horas de las postulaciones vigentes (excluye canceladas y sin horas registradas). */
    public int horasAsignadas(List<ConcursoDocenteDTO> postulaciones) {
        return postulaciones.stream()
                .filter(postulacion -> !estadoEfectivo(postulacion).isCancelado())
                .map(this::horasEfectivas)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    /** Horas disponibles: total − asignadas − transferidas a un rellamado, nunca menor a 0. */
    public Integer horasDisponiblesDe(ConcursoDTO concurso, List<ConcursoDocenteDTO> postulaciones) {
        Integer total = horasTotalesDe(concurso);
        if (total == null) {
            return null;
        }
        int transferidas = concurso.getHorasTransferidas() != null ? concurso.getHorasTransferidas() : 0;
        return Math.max(0, total - horasAsignadas(postulaciones) - transferidas);
    }

    public boolean esCerrado(ConcursoDTO concurso) {
        return concurso.getEstadoConcurso() == EstadoConcurso.FINALIZADO;
    }

    /** Cierre automático: todas las horas cubiertas con designación definitiva. */
    public boolean esListoParaCierre(ConcursoDTO concurso, List<ConcursoDocenteDTO> postulaciones) {
        if (esCerrado(concurso) || postulaciones.isEmpty()) {
            return false;
        }
        List<ConcursoDocenteDTO> vigentes = postulaciones.stream()
                .filter(postulacion -> !estadoEfectivo(postulacion).isCancelado())
                .toList();
        if (vigentes.isEmpty()) {
            return false;
        }
        boolean todosDefinitivos = vigentes.stream()
                .allMatch(postulacion -> estadoEfectivo(postulacion) == EstadoPostulacion.DESIGNACION_DEFINITIVA);
        Integer disponibles = horasDisponiblesDe(concurso, postulaciones);
        return todosDefinitivos && disponibles != null && disponibles <= 0;
    }

    /** Cantidad de postulaciones en un estado efectivo dado. */
    public int contarPorEstado(List<ConcursoDocenteDTO> postulaciones, EstadoPostulacion estado) {
        return (int) postulaciones.stream()
                .filter(postulacion -> estadoEfectivo(postulacion) == estado)
                .count();
    }

    public boolean esRellamado(ConcursoDTO concurso) {
        return Boolean.TRUE.equals(concurso.getEsRellamado());
    }
}
