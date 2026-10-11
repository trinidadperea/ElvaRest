package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.AlertaDTO;
import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import com.elvarest.cliente.dto.enums.EstadoPostulacion;
import com.elvarest.cliente.services.AlertaService;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.ConcursoService;
import com.elvarest.cliente.services.DocenteService;
import com.elvarest.cliente.services.FlujoConcursoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dashboard principal: métricas reales del sistema (docentes, cargos vacantes,
 * alertas sin leer y concursos en curso con su avance de horas), pendientes de
 * atención del flujo de concursos y actividad reciente.
 */
@Controller
public class HomeController {

    private final DocenteService docenteService;
    private final AlertaService alertaService;
    private final ConcursoService concursoService;
    private final CargoService cargoService;
    private final ConcursoDocenteService concursoDocenteService;
    private final FlujoConcursoService flujoService;

    public HomeController(DocenteService docenteService, AlertaService alertaService,
                          ConcursoService concursoService, CargoService cargoService,
                          ConcursoDocenteService concursoDocenteService, FlujoConcursoService flujoService) {
        this.docenteService = docenteService;
        this.alertaService = alertaService;
        this.concursoService = concursoService;
        this.cargoService = cargoService;
        this.concursoDocenteService = concursoDocenteService;
        this.flujoService = flujoService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<DocenteDTO> docentes = listarSeguro(docenteService::listarActivos);
        List<AlertaDTO> alertas = listarSeguro(alertaService::listarActivos);
        List<ConcursoDTO> concursos = listarSeguro(concursoService::listarActivos);
        List<CargoDTO> cargos = listarSeguro(cargoService::listarActivos);
        List<ConcursoDocenteDTO> postulaciones = listarSeguro(concursoDocenteService::listarActivos);

        // --- Métricas principales ---
        long cargosVacantes = cargos.stream().filter(this::esCargoVacante).count();
        List<AlertaDTO> alertasSinLeer = alertas.stream().filter(alerta -> !alerta.isLeida()).toList();
        List<ConcursoDTO> concursosAbiertos = concursos.stream()
                .filter(concurso -> !flujoService.esCerrado(concurso))
                .toList();

        model.addAttribute("cantidadDocentes", docentes.size());
        model.addAttribute("cantidadAlertas", alertas.size());
        model.addAttribute("cantidadConcursos", concursos.size());
        model.addAttribute("cantidadCargos", cargos.size());
        model.addAttribute("cargosVacantes", cargosVacantes);
        model.addAttribute("alertasSinLeer", alertasSinLeer.size());
        model.addAttribute("concursosAbiertos", concursosAbiertos.size());

        // --- Concursos en curso con avance de horas ---
        List<ResumenConcurso> resumenes = new ArrayList<>();
        for (ConcursoDTO concurso : concursosAbiertos) {
            List<ConcursoDocenteDTO> propias = flujoService.postulantesDe(postulaciones, concurso.getId());
            resumenes.add(new ResumenConcurso(
                    concurso,
                    flujoService.horasTotalesDe(concurso),
                    flujoService.horasDisponiblesDe(concurso, propias),
                    (int) propias.stream().filter(p -> !flujoService.estadoEfectivo(p).isCancelado()).count(),
                    flujoService.contarPorEstado(propias, EstadoPostulacion.PENDIENTE_CERTIFICADOS),
                    flujoService.contarPorEstado(propias, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION),
                    flujoService.contarPorEstado(propias, EstadoPostulacion.DESIGNACION_DEFINITIVA),
                    flujoService.contarPorEstado(propias, EstadoPostulacion.CANCELADO_INEFECTIVO),
                    flujoService.esListoParaCierre(concurso, propias),
                    flujoService.esRellamado(concurso)));
        }
        model.addAttribute("concursosEnCurso", resumenes);

        // Próximos cierres: concursos abiertos con fecha de cierre, ordenados por cercanía
        List<ResumenConcurso> proximosCierres = resumenes.stream()
                .filter(resumen -> resumen.getConcurso().getFechaCierre() != null)
                .sorted(Comparator.comparing(resumen -> resumen.getConcurso().getFechaCierre()))
                .limit(5)
                .toList();
        model.addAttribute("proximosCierres", proximosCierres);

        // --- Alertas sin leer (máximo 5) ---
        model.addAttribute("alertasRecientes", alertasSinLeer.stream().limit(5).toList());

        // --- Pendientes de atención del flujo ---
        Map<Long, ConcursoDTO> concursosPorId = new HashMap<>();
        concursos.forEach(concurso -> concursosPorId.put(concurso.getId(), concurso));
        List<PendienteAtencion> pendientes = new ArrayList<>();
        for (ConcursoDocenteDTO postulacion : postulaciones) {
            if (postulacion.getConcurso() == null) {
                continue;
            }
            ConcursoDTO concurso = concursosPorId.get(postulacion.getConcurso().getId());
            if (concurso == null || flujoService.esCerrado(concurso)) {
                continue;
            }
            EstadoPostulacion estado = flujoService.estadoEfectivo(postulacion);
            if (estado == EstadoPostulacion.PENDIENTE_CERTIFICADOS
                    || estado == EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                pendientes.add(new PendienteAtencion(concurso, postulacion, estado));
            }
        }
        model.addAttribute("pendientesAtencion", pendientes.stream().limit(8).toList());

        // --- Actividad reciente: últimas tomas de horas registradas ---
        List<ConcursoDocenteDTO> actividadReciente = postulaciones.stream()
                .sorted(Comparator.comparing(ConcursoDocenteDTO::getFechaEmision,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .toList();
        Map<Long, EstadoPostulacion> estadosRecientes = new HashMap<>();
        for (ConcursoDocenteDTO postulacion : actividadReciente) {
            if (postulacion.getId() != null) {
                estadosRecientes.put(postulacion.getId(), flujoService.estadoEfectivo(postulacion));
            }
        }
        model.addAttribute("actividadReciente", actividadReciente);
        model.addAttribute("estadosRecientes", estadosRecientes);

        // --- Gráfico: postulaciones por estado efectivo ---
        model.addAttribute("totalPostulaciones", postulaciones.size());
        model.addAttribute("chartLabels", List.of(
                "Pendientes de certificados",
                "Pendientes de posesión",
                "Designación definitiva",
                "Canceladas / inefectivas"));
        model.addAttribute("chartValues", List.of(
                flujoService.contarPorEstado(postulaciones, EstadoPostulacion.PENDIENTE_CERTIFICADOS),
                flujoService.contarPorEstado(postulaciones, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION),
                flujoService.contarPorEstado(postulaciones, EstadoPostulacion.DESIGNACION_DEFINITIVA),
                flujoService.contarPorEstado(postulaciones, EstadoPostulacion.CANCELADO_INEFECTIVO)));

        return "dashboard/index";
    }

    /** Un cargo sin designaciones vigentes (ni activas ni pendientes) se considera vacante. */
    private boolean esCargoVacante(CargoDTO cargo) {
        List<DesignacionDTO> designaciones = cargo.getDesignaciones();
        if (designaciones == null || designaciones.isEmpty()) {
            return true;
        }
        return designaciones.stream().noneMatch(designacion ->
                designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO
                        || designacion.getEstadoDesignacion() == EstadoDesignacion.PENDIENTE);
    }

    private <T> List<T> listarSeguro(Supplier<List<T>> supplier) {
        try {
            List<T> lista = supplier.get();
            return lista == null ? List.of() : lista;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Resumen de un concurso abierto para las tarjetas del dashboard. */
    public static class ResumenConcurso {
        private final ConcursoDTO concurso;
        private final Integer horasTotales;
        private final Integer horasDisponibles;
        private final int vigentes;
        private final int pendientesCertificados;
        private final int pendientesPosesion;
        private final int designados;
        private final int canceladas;
        private final boolean listoParaCierre;
        private final boolean rellamado;

        public ResumenConcurso(ConcursoDTO concurso, Integer horasTotales, Integer horasDisponibles,
                               int vigentes, int pendientesCertificados, int pendientesPosesion,
                               int designados, int canceladas, boolean listoParaCierre, boolean rellamado) {
            this.concurso = concurso;
            this.horasTotales = horasTotales;
            this.horasDisponibles = horasDisponibles;
            this.vigentes = vigentes;
            this.pendientesCertificados = pendientesCertificados;
            this.pendientesPosesion = pendientesPosesion;
            this.designados = designados;
            this.canceladas = canceladas;
            this.listoParaCierre = listoParaCierre;
            this.rellamado = rellamado;
        }

        public ConcursoDTO getConcurso() { return concurso; }
        public Integer getHorasTotales() { return horasTotales; }
        public Integer getHorasDisponibles() { return horasDisponibles; }
        public int getVigentes() { return vigentes; }
        public int getPendientesCertificados() { return pendientesCertificados; }
        public int getPendientesPosesion() { return pendientesPosesion; }
        public int getDesignados() { return designados; }
        public int getCanceladas() { return canceladas; }
        public boolean isListoParaCierre() { return listoParaCierre; }
        public boolean isRellamado() { return rellamado; }
    }

    /** Postulación que requiere una acción (validar certificados o confirmar posesión). */
    public static class PendienteAtencion {
        private final ConcursoDTO concurso;
        private final ConcursoDocenteDTO postulacion;
        private final EstadoPostulacion estado;

        public PendienteAtencion(ConcursoDTO concurso, ConcursoDocenteDTO postulacion, EstadoPostulacion estado) {
            this.concurso = concurso;
            this.postulacion = postulacion;
            this.estado = estado;
        }

        public ConcursoDTO getConcurso() { return concurso; }
        public ConcursoDocenteDTO getPostulacion() { return postulacion; }
        public EstadoPostulacion getEstado() { return estado; }
    }
}
