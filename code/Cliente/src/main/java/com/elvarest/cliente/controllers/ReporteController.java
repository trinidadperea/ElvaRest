package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.enums.CaracterDesignacion;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.ConcursoService;
import com.elvarest.cliente.services.DesignacionService;
import com.elvarest.cliente.services.DocenteService;
import com.elvarest.cliente.services.FlujoConcursoService;
import com.elvarest.cliente.services.ReporteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Módulo de reportes según el flujo funcional: suplentes vigentes,
 * historial de designaciones y concursos con su avance de horas.
 *
 * Todos los datos se resuelven con los servicios reales de la API; las horas
 * del flujo de concursos se calculan con {@link FlujoConcursoService}.
 */
@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private final DocenteService docenteService;
    private final CargoService cargoService;
    private final DesignacionService designacionService;
    private final ConcursoService concursoService;
    private final ConcursoDocenteService concursoDocenteService;
    private final FlujoConcursoService flujoService;
    private final ReporteService reporteService;

    public ReporteController(DocenteService docenteService, CargoService cargoService,
                             DesignacionService designacionService, ConcursoService concursoService,
                             ConcursoDocenteService concursoDocenteService, FlujoConcursoService flujoService,
                             ReporteService reporteService) {
        this.docenteService = docenteService;
        this.cargoService = cargoService;
        this.designacionService = designacionService;
        this.concursoService = concursoService;
        this.concursoDocenteService = concursoDocenteService;
        this.flujoService = flujoService;
        this.reporteService = reporteService;
    }

    /** PDF de un concurso generado por el Servidor; se abre en una pestaña nueva del navegador. */
    @GetMapping("/concurso/{concursoId}/pdf")
    public ResponseEntity<byte[]> pdfConcurso(@PathVariable Long concursoId) {
        try {
            byte[] pdf = reporteService.pdfConcurso(concursoId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=reporte-concurso-" + concursoId + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (HttpClientErrorException.NotFound e) {
            return mensajeError(HttpStatus.NOT_FOUND, "El concurso solicitado no existe o fue eliminado.");
        } catch (Exception e) {
            return mensajeError(HttpStatus.BAD_GATEWAY, "No se pudo generar el reporte del concurso. Intente nuevamente.");
        }
    }

    private ResponseEntity<byte[]> mensajeError(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(mensaje.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping
    public String listar(Model model) {
        List<DocenteDTO> docentes = listarSeguro(docenteService::listarActivos);
        List<CargoDTO> cargos = listarSeguro(cargoService::listarActivos);
        List<DesignacionDTO> designaciones = listarSeguro(designacionService::listarActivos);
        List<ConcursoDTO> concursos = listarSeguro(concursoService::listarActivos);
        List<ConcursoDocenteDTO> postulaciones = listarSeguro(concursoDocenteService::listarActivos);

        // El DTO de designación no referencia al cargo: se resuelve por el cargo que la contiene.
        Map<Long, CargoDTO> cargoPorDesignacion = mapaCargoPorDesignacion(cargos);

        // --- 1. Suplentes vigentes: designaciones activas con carácter de suplencia ---
        List<SuplenteVigente> suplentes = designaciones.stream()
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .filter(designacion -> designacion.getCaracterDesignacion() == CaracterDesignacion.SUPLENTE
                        || designacion.getCaracterDesignacion() == CaracterDesignacion.SUPLENTE_CARGO_VACANTE)
                .map(designacion -> new SuplenteVigente(designacion, cargoPorDesignacion.get(designacion.getId())))
                .toList();
        model.addAttribute("suplentesVigentes", suplentes);

        // --- 2. Historial de designaciones (más recientes primero) ---
        List<DesignacionHistorial> historial = designaciones.stream()
                .filter(designacion -> designacion.getDocente() != null)
                .sorted(Comparator.comparing(DesignacionDTO::getFechaInicioEfectiva,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(designacion -> new DesignacionHistorial(designacion, cargoPorDesignacion.get(designacion.getId())))
                .toList();
        model.addAttribute("historialDesignaciones", historial);

        // --- 3. Concursos con avance de horas y postulantes vigentes ---
        List<ConcursoReporte> concursosReporte = new ArrayList<>();
        for (ConcursoDTO concurso : concursos) {
            List<ConcursoDocenteDTO> propias = flujoService.postulantesDe(postulaciones, concurso.getId());
            concursosReporte.add(new ConcursoReporte(
                    concurso,
                    flujoService.horasTotalesDe(concurso),
                    flujoService.horasDisponiblesDe(concurso, propias),
                    (int) propias.stream()
                            .filter(postulacion -> !flujoService.estadoEfectivo(postulacion).isCancelado())
                            .count(),
                    flujoService.esCerrado(concurso),
                    flujoService.esRellamado(concurso),
                    flujoService.esListoParaCierre(concurso, propias)));
        }
        concursosReporte.sort(Comparator.comparing(
                (ConcursoReporte reporte) -> reporte.getConcurso().getFechaConcurso() != null
                        ? reporte.getConcurso().getFechaConcurso().toLocalDate()
                        : reporte.getConcurso().getFechaApertura(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        model.addAttribute("concursosReporte", concursosReporte);

        // --- Métricas de encabezado ---
        model.addAttribute("cantidadDocentes", docentes.size());
        model.addAttribute("cantidadSuplentes", suplentes.size());
        model.addAttribute("cantidadDesignacionesActivas", designaciones.stream()
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .count());
        model.addAttribute("cantidadConcursosAbiertos", concursosReporte.stream()
                .filter(reporte -> !reporte.isCerrado())
                .count());

        // --- Gráfico: horas cubiertas vs. disponibles por concurso abierto ---
        List<String> chartLabels = new ArrayList<>();
        List<Integer> chartCubiertas = new ArrayList<>();
        List<Integer> chartDisponibles = new ArrayList<>();
        for (ConcursoReporte reporte : concursosReporte) {
            if (reporte.isCerrado() || reporte.getHorasTotales() == null || reporte.getHorasTotales() <= 0) {
                continue;
            }
            int disponibles = reporte.getHorasDisponibles() == null ? 0 : reporte.getHorasDisponibles();
            chartLabels.add(abreviar(reporte.getConcurso().getDescripcion(), 30));
            chartCubiertas.add(Math.max(0, reporte.getHorasTotales() - disponibles));
            chartDisponibles.add(disponibles);
            if (chartLabels.size() >= 8) {
                break;
            }
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartCubiertas", chartCubiertas);
        model.addAttribute("chartDisponibles", chartDisponibles);

        return "reportes/lista";
    }

    /** Asocia cada designación con el cargo que la contiene. */
    private Map<Long, CargoDTO> mapaCargoPorDesignacion(List<CargoDTO> cargos) {
        Map<Long, CargoDTO> mapa = new HashMap<>();
        for (CargoDTO cargo : cargos) {
            if (cargo.getDesignaciones() == null) {
                continue;
            }
            for (DesignacionDTO designacion : cargo.getDesignaciones()) {
                if (designacion.getId() != null) {
                    mapa.put(designacion.getId(), cargo);
                }
            }
        }
        return mapa;
    }

    private String abreviar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return "Concurso sin descripción";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }

    private <T> List<T> listarSeguro(Supplier<List<T>> supplier) {
        try {
            List<T> lista = supplier.get();
            return lista == null ? List.of() : lista;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Suplente con designación vigente y el cargo asociado. */
    public static class SuplenteVigente {
        private final DesignacionDTO designacion;
        private final CargoDTO cargo;

        public SuplenteVigente(DesignacionDTO designacion, CargoDTO cargo) {
            this.designacion = designacion;
            this.cargo = cargo;
        }

        public DesignacionDTO getDesignacion() { return designacion; }
        public CargoDTO getCargo() { return cargo; }
    }

    /** Fila del historial de designaciones. */
    public static class DesignacionHistorial {
        private final DesignacionDTO designacion;
        private final CargoDTO cargo;

        public DesignacionHistorial(DesignacionDTO designacion, CargoDTO cargo) {
            this.designacion = designacion;
            this.cargo = cargo;
        }

        public DesignacionDTO getDesignacion() { return designacion; }
        public CargoDTO getCargo() { return cargo; }
    }

    /** Resumen de un concurso para el reporte. */
    public static class ConcursoReporte {
        private final ConcursoDTO concurso;
        private final Integer horasTotales;
        private final Integer horasDisponibles;
        private final int postulantesVigentes;
        private final boolean cerrado;
        private final boolean rellamado;
        private final boolean listoParaCierre;

        public ConcursoReporte(ConcursoDTO concurso, Integer horasTotales, Integer horasDisponibles,
                               int postulantesVigentes, boolean cerrado, boolean rellamado, boolean listoParaCierre) {
            this.concurso = concurso;
            this.horasTotales = horasTotales;
            this.horasDisponibles = horasDisponibles;
            this.postulantesVigentes = postulantesVigentes;
            this.cerrado = cerrado;
            this.rellamado = rellamado;
            this.listoParaCierre = listoParaCierre;
        }

        public ConcursoDTO getConcurso() { return concurso; }
        public Integer getHorasTotales() { return horasTotales; }
        public Integer getHorasDisponibles() { return horasDisponibles; }
        public int getPostulantesVigentes() { return postulantesVigentes; }
        public boolean isCerrado() { return cerrado; }
        public boolean isRellamado() { return rellamado; }
        public boolean isListoParaCierre() { return listoParaCierre; }
    }
}
