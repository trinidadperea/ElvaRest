package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.TipoCertificadoDTO;
import com.elvarest.cliente.dto.enums.EstadoConcurso;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import com.elvarest.cliente.dto.enums.EstadoPostulacion;
import com.elvarest.cliente.dto.enums.MotivoVacancia;
import com.elvarest.cliente.dto.enums.TipoCargo;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.ConcursoService;
import com.elvarest.cliente.services.DesignacionService;
import com.elvarest.cliente.services.DocenteService;
import com.elvarest.cliente.services.FlujoConcursoService;
import com.elvarest.cliente.services.TipoCertificadoService;
import com.elvarest.cliente.services.ValidacionCertificadosService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controlador del módulo de concursos con el flujo de toma de horas:
 * 1) registrar / modificar toma de horas,
 * 2) validar certificados,
 * 3) confirmar toma de posesión (o declarar inefectivo),
 * 4) cierre y rellamado.
 *
 * Los datos del flujo (estado de postulación, horas, certificados, observaciones y
 * datos de rellamado) se persisten en el Servidor.
 */
@Controller
@RequestMapping("/concursos")
public class ConcursoController extends BaseController<ConcursoDTO, Long> {

    private final ConcursoDocenteService concursoDocenteService;
    private final DocenteService docenteService;
    private final CargoService cargoService;
    private final DesignacionService designacionService;
    private final FlujoConcursoService flujoService;
    private final CertificadoService certificadoService;
    private final TipoCertificadoService tipoCertificadoService;
    private final ValidacionCertificadosService validacionCertificadosService;

    private static final String CARGO_CONCURSO_ABIERTO =
            "El cargo ya tiene un concurso abierto: ciérrelo antes de abrir otro.";

    public ConcursoController(ConcursoService service, ConcursoDocenteService concursoDocenteService,
                              DocenteService docenteService, CargoService cargoService,
                              DesignacionService designacionService,
                              FlujoConcursoService flujoService,
                              CertificadoService certificadoService,
                              TipoCertificadoService tipoCertificadoService,
                              ValidacionCertificadosService validacionCertificadosService) {
        super(service);
        this.concursoDocenteService = concursoDocenteService;
        this.docenteService = docenteService;
        this.cargoService = cargoService;
        this.designacionService = designacionService;
        this.flujoService = flujoService;
        this.certificadoService = certificadoService;
        this.tipoCertificadoService = tipoCertificadoService;
        this.validacionCertificadosService = validacionCertificadosService;
        initController(new ConcursoDTO(), "Lista de concursos", "Editar concurso", "concursos/");
    }

    // ------------------------------------------------------------------
    // Listado y detalle
    // ------------------------------------------------------------------

    @Override
    @GetMapping("/list")
    public String listar(Model model) {
        try {
            List<ConcursoDTO> concursos = service.listarActivos();
            concursos.forEach(this::aplicarTransicionEstado);
            List<ConcursoDocenteDTO> postulaciones = concursoDocenteService.listarActivos();
            Map<Long, Integer> cantidadPostulantes = new HashMap<>();
            Map<Long, ConcursoDocenteDTO> ganadores = new HashMap<>();
            Map<Long, Integer> horasTotalesMap = new HashMap<>();
            Map<Long, Integer> horasDisponiblesMap = new HashMap<>();
            Map<Long, String> fechasConcursos = new HashMap<>();
            Map<Long, String> aperturasConcursos = new HashMap<>();

            for (ConcursoDTO concurso : concursos) {
                List<ConcursoDocenteDTO> propias = postulaciones.stream()
                        .filter(postulacion -> postulacion.getConcurso() != null
                                && Objects.equals(postulacion.getConcurso().getId(), concurso.getId()))
                        .toList();
                int vigentes = (int) propias.stream().filter(postulacion -> !estadoEfectivo(postulacion).isCancelado()).count();
                cantidadPostulantes.put(concurso.getId(), vigentes);
                propias.stream().filter(ConcursoDocenteDTO::isGanador).findFirst()
                        .ifPresent(ganador -> ganadores.put(concurso.getId(), ganador));
                Integer total = horasTotalesDe(concurso);
                if (total != null) {
                    horasTotalesMap.put(concurso.getId(), total);
                }
                Integer disponibles = horasDisponiblesDe(concurso, propias);
                if (disponibles != null) {
                    horasDisponiblesMap.put(concurso.getId(), disponibles);
                }
                fechasConcursos.put(concurso.getId(), fechaDelConcurso(concurso));
                aperturasConcursos.put(concurso.getId(), concurso.getFechaApertura() != null
                        ? concurso.getFechaApertura().toString() : "");
            }

            model.addAttribute("items", concursos);
            model.addAttribute("cantidadPostulantes", cantidadPostulantes);
            model.addAttribute("ganadores", ganadores);
            model.addAttribute("horasTotalesMap", horasTotalesMap);
            model.addAttribute("horasDisponiblesMap", horasDisponiblesMap);
            model.addAttribute("fechasConcursos", fechasConcursos);
            model.addAttribute("aperturasConcursos", aperturasConcursos);
            model.addAttribute("titleList", titleList);
            model.addAttribute("nameEntityLower", nameEntityLower);
        } catch (Exception e) {
            model.addAttribute("items", List.of());
            model.addAttribute("cantidadPostulantes", Map.of());
            model.addAttribute("ganadores", Map.of());
            model.addAttribute("horasTotalesMap", Map.of());
            model.addAttribute("horasDisponiblesMap", Map.of());
            model.addAttribute("fechasConcursos", Map.of());
            model.addAttribute("aperturasConcursos", Map.of());
            model.addAttribute("msgError", "No se pudo recuperar el listado de concursos: " + e.getMessage());
        }
        return viewList;
    }

    /**
     * Fecha que representa al concurso en el listado (la misma que se muestra): la de toma de
     * horas o, si todavía no se registró, la de cierre. Se usa para el filtro por rango de fechas.
     */
    private String fechaDelConcurso(ConcursoDTO concurso) {
        LocalDate fecha = concurso.getFechaConcurso() != null
                ? concurso.getFechaConcurso().toLocalDate() : concurso.getFechaCierre();
        return fecha != null ? fecha.toString() : "";
    }

    @Override
    protected void postConsulta(ConcursoDTO concurso) {
        aplicarTransicionEstado(concurso);
        List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concurso.getId());
        Map<Long, EstadoPostulacion> estadosPostulacion = new HashMap<>();
        Map<Long, Integer> horasPostulacion = new HashMap<>();
        Map<Long, String> observaciones = new HashMap<>();
        List<ConcursoDocenteDTO> pendientesCertificados = new ArrayList<>();
        List<ConcursoDocenteDTO> pendientesPosesion = new ArrayList<>();
        int designados = 0;
        int canceladas = 0;

        for (ConcursoDocenteDTO postulante : postulantes) {
            if (postulante.getId() == null) {
                continue;
            }
            EstadoPostulacion estado = estadoEfectivo(postulante);
            estadosPostulacion.put(postulante.getId(), estado);
            Integer horas = horasEfectivas(postulante);
            if (horas != null) {
                horasPostulacion.put(postulante.getId(), horas);
            }
            String observacion = postulante.getObservacion();
            if (observacion != null && !observacion.isBlank()) {
                observaciones.put(postulante.getId(), observacion);
            }
            if (estado == EstadoPostulacion.PENDIENTE_CERTIFICADOS) {
                pendientesCertificados.add(postulante);
            } else if (estado == EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                pendientesPosesion.add(postulante);
            } else if (estado == EstadoPostulacion.DESIGNACION_DEFINITIVA) {
                designados++;
            } else {
                canceladas++;
            }
        }

        Integer horasTotales = horasTotalesDe(concurso);
        int horasAsignadas = horasAsignadas(postulantes);
        Integer horasDisponibles = horasDisponiblesDe(concurso, postulantes);
        int porcentaje = 0;
        if (horasTotales != null && horasTotales > 0) {
            porcentaje = Math.min(100, (int) Math.round(horasAsignadas * 100.0 / horasTotales));
        }
        boolean cerrado = esCerrado(concurso);
        boolean listoParaCierre = esListoParaCierre(concurso, postulantes);

        ConcursoDTO concursoOrigen = null;
        if (concurso.getConcursoOrigenId() != null) {
            try {
                concursoOrigen = service.obtener(concurso.getConcursoOrigenId());
            } catch (Exception ignored) {
                concursoOrigen = null;
            }
        }

        // Atributos del flujo nuevo
        model.addAttribute("postulantes", postulantes);
        model.addAttribute("estadosPostulacion", estadosPostulacion);
        model.addAttribute("horasPostulacion", horasPostulacion);
        model.addAttribute("observaciones", observaciones);
        model.addAttribute("pendientesCertificados", pendientesCertificados);
        model.addAttribute("pendientesPosesion", pendientesPosesion);
        model.addAttribute("designados", designados);
        model.addAttribute("canceladas", canceladas);
        model.addAttribute("horasTotales", horasTotales);
        model.addAttribute("horasAsignadas", horasAsignadas);
        model.addAttribute("horasDisponibles", horasDisponibles);
        model.addAttribute("porcentajeHoras", porcentaje);
        model.addAttribute("cerrado", cerrado);
        model.addAttribute("esDePreceptor", esDePreceptor(concurso));
        model.addAttribute("preceptorCubierto", preceptorCubierto(concurso, postulantes));
        model.addAttribute("listoParaCierre", listoParaCierre);
        model.addAttribute("esRellamado", flujoService.esRellamado(concurso));
        model.addAttribute("concursoOrigen", concursoOrigen);

        // Compatibilidad con nombres anteriores del detalle
        model.addAttribute("cantidadPostulantes", postulantes.size());
        model.addAttribute("ganador", postulantes.stream()
                .filter(ConcursoDocenteDTO::isGanador)
                .findFirst()
                .orElse(null));
    }

    // ------------------------------------------------------------------
    // Alta / edición de concursos
    // ------------------------------------------------------------------

    @Override
    protected void preAlta() {
        model.addAttribute("cargos", cargosDisponibles(null));
    }

    /**
     * Alta de concurso desde "Cargo → Ver → Abrir concurso para este cargo":
     * el cargo viene fijado por contexto y no se puede modificar en el formulario.
     */
    @GetMapping("/nuevo-desde-cargo/{cargoId}")
    public String crearDesdeCargo(@PathVariable Long cargoId, Model model, RedirectAttributes attributes) {
        try {
            CargoDTO cargo = cargoService.obtener(cargoId);
            if (cargo == null) {
                throw new IllegalArgumentException("No se encontró el cargo.");
            }
            if (cargoTieneConcursoAbierto(cargoId, null)) {
                throw new IllegalArgumentException(CARGO_CONCURSO_ABIERTO);
            }
            ConcursoDTO concurso = new ConcursoDTO();
            concurso.setCargo(cargo);
            concurso.setCargoFijado(true);
            this.model = model;
            model.addAttribute("item", concurso);
            model.addAttribute("isDisabled", false);
            model.addAttribute("titleEdit", titleEdit);
            model.addAttribute("nameEntityLower", nameEntityLower);
            return viewCreate;
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/cargos/" + cargoId;
        }
    }

    @Override
    protected void preModificacion() {
        if (model == null) {
            return;
        }
        Object item = model.getAttribute("item");
        ConcursoDTO actual = item instanceof ConcursoDTO concurso ? concurso : null;
        if (!model.containsAttribute("cargos")) {
            model.addAttribute("cargos", cargosDisponibles(actual));
        }
        // Al editar, si no hay fecha/hora de concurso se propone la fecha de cierre registrada
        Object disabled = model.getAttribute("isDisabled");
        if (actual != null && Boolean.FALSE.equals(disabled)
                && actual.getFechaConcurso() == null && actual.getFechaCierre() != null) {
            actual.setFechaConcurso(actual.getFechaCierre().atStartOfDay());
        }
        // El motivo se deduce del texto guardado para los concursos anteriores al selector. En el
        // formulario, origenVacancia se edita como detalle del motivo y se muestra sin el motivo
        // ("Renuncia del titular" -> "del titular") para no recomponer "Renuncia – Renuncia" al
        // guardar. En el detalle (isDisabled) se conserva el texto completo.
        if (actual != null) {
            if (actual.getMotivoVacancia() == null) {
                actual.setMotivoVacancia(MotivoVacancia.desdeTexto(actual.getOrigenVacancia()));
            }
            if (Boolean.FALSE.equals(disabled)) {
                actual.setOrigenVacancia(MotivoVacancia.detalleDesdeTexto(
                        actual.getMotivoVacancia(), actual.getOrigenVacancia()));
            }
        }
    }

    /**
     * Reemplaza el guardado genérico para normalizar los campos del flujo:
     * fecha del concurso, horas totales (cargo como respaldo) y datos de rellamado.
     */
    @Override
    @PostMapping("/actualizar")
    public String actualizar(@ModelAttribute("item") ConcursoDTO entidad,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             RedirectAttributes attributes, Model model) {
        try {
            this.model = model;
            // El formulario envía en origenVacancia el detalle libre del motivo elegido.
            if (entidad.getOrigenVacancia() != null) {
                String detalle = entidad.getOrigenVacancia().trim();
                entidad.setOrigenVacancia(detalle.isEmpty() ? null : detalle);
            }
            if (entidad.getProfesorReemplazante() != null) {
                String reemplazante = entidad.getProfesorReemplazante().trim();
                entidad.setProfesorReemplazante(reemplazante.isEmpty() ? null : reemplazante);
            }
            if (entidad.getCargo() != null && entidad.getCargo().getId() == null) {
                entidad.setCargo(null);
            }
            if (entidad.getFechaConcurso() != null) {
                entidad.setFechaCierre(entidad.getFechaConcurso().toLocalDate());
            }
            if (entidad.getId() == null) {
                // Regla de estado: todo concurso nuevo nace PUBLICADO; el estado no se selecciona manualmente.
                entidad.setEstadoConcurso(EstadoConcurso.PUBLICADO);
                if (entidad.getDescripcion() == null || entidad.getDescripcion().isBlank()) {
                    throw new IllegalArgumentException("La descripción del concurso es obligatoria.");
                }
                if (entidad.getConcursoOrigenId() != null) {
                    // Alta como rellamado: hereda cargo y horas remanentes del concurso origen
                    ConcursoDTO origen = obtenerConcurso(entidad.getConcursoOrigenId());
                    if (!esCerrado(origen)) {
                        throw new IllegalArgumentException("El concurso origen debe estar cerrado para generar un rellamado.");
                    }
                    List<ConcursoDocenteDTO> postulantesOrigen = postulantesDelConcurso(origen.getId());
                    Integer remanentes = horasDisponiblesDe(origen, postulantesOrigen);
                    if (remanentes == null || remanentes <= 0) {
                        throw new IllegalArgumentException("El concurso origen no tiene horas remanentes para transferir.");
                    }
                    entidad.setCargo(origen.getCargo());
                    if (cargoTieneConcursoAbierto(entidad.getCargo() != null ? entidad.getCargo().getId() : null, null)) {
                        throw new IllegalArgumentException(CARGO_CONCURSO_ABIERTO);
                    }
                    entidad.setCaracterDesignacion(origen.getCaracterDesignacion());
                    // El rellamado cubre la misma vacancia: hereda el origen capturado en el concurso original.
                    entidad.setOrigenVacancia(origen.getOrigenVacancia());
                    entidad.setMotivoVacancia(origen.getMotivoVacancia() != null
                            ? origen.getMotivoVacancia()
                            : MotivoVacancia.desdeTexto(origen.getOrigenVacancia()));
                    entidad.setProfesorReemplazante(origen.getProfesorReemplazante());
                    entidad.setEsRellamado(true);
                    entidad.setHorasTotales(remanentes);
                    // Las horas remanentes quedan transferidas al rellamado: el origen queda sin disponibles.
                    origen.setHorasTransferidas(remanentes);
                    service.modificar(origen.getId(), origen);
                } else {
                    // Alta directa: el cargo, la fecha del concurso y la designación son obligatorios.
                    if (entidad.getCargo() == null) {
                        throw new IllegalArgumentException("Seleccione el cargo vacante del concurso.");
                    }
                    if (entidad.getFechaConcurso() == null) {
                        throw new IllegalArgumentException("Indique la fecha y hora del concurso.");
                    }
                    if (entidad.getCaracterDesignacion() == null) {
                        throw new IllegalArgumentException("Seleccione la designación (carácter de designación) del concurso.");
                    }
                    aplicarMotivoVacancia(entidad);
                    if (cargoTieneConcursoAbierto(entidad.getCargo().getId(), null)) {
                        throw new IllegalArgumentException(CARGO_CONCURSO_ABIERTO);
                    }
                    entidad.setEsRellamado(false);
                }
                if (entidad.getFechaApertura() == null) {
                    entidad.setFechaApertura(LocalDate.now());
                }
                if ((entidad.getHorasTotales() == null || entidad.getHorasTotales() <= 0)
                        && entidad.getCargo() != null && entidad.getCargo().getCargaHoraria() > 0) {
                    entidad.setHorasTotales(entidad.getCargo().getCargaHoraria());
                }
                if (entidad.getHorasTotales() == null || entidad.getHorasTotales() <= 0) {
                    throw new IllegalArgumentException("El concurso no tiene horas totales: indique las horas totales o complete la carga horaria del cargo.");
                }
                service.alta(entidad);
            } else {
                // En edición el formulario no envía estado, fechas ni datos de rellamado: se preservan los valores actuales.
                ConcursoDTO actual = null;
                try {
                    actual = service.obtener(entidad.getId());
                } catch (RuntimeException ignored) {
                    actual = null;
                }
                if (actual != null) {
                    if (entidad.getEstadoConcurso() == null) {
                        entidad.setEstadoConcurso(actual.getEstadoConcurso());
                    }
                    if (entidad.getFechaApertura() == null) {
                        entidad.setFechaApertura(actual.getFechaApertura());
                    }
                    if (entidad.getFechaCierre() == null) {
                        entidad.setFechaCierre(actual.getFechaCierre());
                    }
                    if (entidad.getHorasTotales() == null) {
                        entidad.setHorasTotales(actual.getHorasTotales());
                    }
                    if (entidad.getHorasTransferidas() == null) {
                        entidad.setHorasTransferidas(actual.getHorasTransferidas());
                    }
                    entidad.setEsRellamado(actual.getEsRellamado());
                    entidad.setConcursoOrigenId(actual.getConcursoOrigenId());
                }
                aplicarMotivoVacancia(entidad);
                service.modificar(entidad.getId(), entidad);
            }

            attributes.addFlashAttribute("msgExito", "La acción fue realizada correctamente.");
            return redirectList;
        } catch (RuntimeException e) {
            model.addAttribute("msgError", e.getMessage());
            model.addAttribute("item", entidad);
            model.addAttribute("isDisabled", false);
            model.addAttribute("titleEdit", titleEdit);
            model.addAttribute("nameEntityLower", nameEntityLower);
            model.addAttribute("cargos", cargosDisponibles(entidad));
            if (entidad.getId() == null) {
                return viewCreate;
            }
            return viewEdit;
        }
    }

    // ------------------------------------------------------------------
    // Paso 1: registrar / modificar toma de horas
    // ------------------------------------------------------------------

    @GetMapping("/{concursoId}/postular")
    public String mostrarFormularioPostulacion(@PathVariable Long concursoId, Model model,
                                               RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarAbierto(concurso, "no se pueden registrar tomas de horas");
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            ConcursoDocenteDTO postulacion = new ConcursoDocenteDTO();
            postulacion.setConcurso(concurso);
            model.addAttribute("item", postulacion);
            model.addAttribute("concurso", concurso);
            model.addAttribute("docentes", docentesDisponiblesParaPostular(concurso, postulantes));
            model.addAttribute("esDePreceptor", esDePreceptor(concurso));
            model.addAttribute("preceptorCubierto", preceptorCubierto(concurso, postulantes));
            model.addAttribute("horasTotales", horasTotalesDe(concurso));
            model.addAttribute("horasDisponibles", horasDisponiblesDe(concurso, postulantes));
            return "concursos/postular";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
    }

    @PostMapping("/{concursoId}/postular")
    public String postular(@PathVariable Long concursoId,
                           @ModelAttribute("item") ConcursoDocenteDTO postulacion,
                           RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarAbierto(concurso, "no se pueden registrar tomas de horas");
            Long docenteId = postulacion.getDocente() == null ? null : postulacion.getDocente().getId();
            if (docenteId == null) {
                throw new IllegalArgumentException("Debe seleccionar un docente.");
            }
            boolean docenteActivo = docenteService.listarActivos().stream()
                    .anyMatch(docente -> Objects.equals(docente.getId(), docenteId));
            if (!docenteActivo) {
                throw new IllegalArgumentException("Solo se pueden registrar docentes activos.");
            }
            DocenteDTO docente = docenteService.obtener(docenteId);
            boolean esDePreceptor = esDePreceptor(concurso);
            boolean esPreceptor = docente.getTipo() == TipoCargo.PRECEPTOR;
            if (esDePreceptor && !esPreceptor) {
                throw new IllegalArgumentException("Este cargo es de preceptor: solo se pueden postular preceptores.");
            }
            if (!esDePreceptor && esPreceptor) {
                throw new IllegalArgumentException("Este cargo es docente: no se pueden postular preceptores.");
            }
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            boolean yaPostulado = postulantes.stream()
                    .anyMatch(actual -> !estadoEfectivo(actual).isCancelado()
                            && actual.getDocente() != null
                            && Objects.equals(actual.getDocente().getId(), docenteId));
            if (yaPostulado) {
                throw new IllegalArgumentException("El docente ya tiene una toma de horas vigente en este concurso.");
            }
            if (preceptorCubierto(concurso, postulantes)) {
                throw new IllegalArgumentException("El concurso de preceptor admite una sola persona y ya tiene una postulación vigente.");
            }
            boolean designacionEfectivaOrigen = docentesConDesignacionEfectivaDelOrigen(concurso).stream()
                    .anyMatch(designado -> Objects.equals(designado.getId(), docenteId));
            if (designacionEfectivaOrigen) {
                throw new IllegalArgumentException("El docente ya obtuvo una designación efectiva en el concurso origen para este mismo cargo, materia y curso: no puede volver a seleccionarse en el rellamado.");
            }
            boolean designacionActivaDelCargo = docentesConDesignacionActivaDelCargo(concurso).stream()
                    .anyMatch(designado -> Objects.equals(designado.getId(), docenteId));
            if (designacionActivaDelCargo) {
                throw new IllegalArgumentException("El docente tiene una designación efectiva vigente en este mismo cargo, materia y curso: no puede postularse a su propio reemplazo.");
            }
            Integer disponibles = horasDisponiblesDe(concurso, postulantes);
            if (esDePreceptor) {
                // El preceptor toma todas las horas disponibles del concurso.
                if (disponibles == null || disponibles < 1) {
                    throw new IllegalArgumentException("No quedan horas disponibles en el concurso.");
                }
                postulacion.setHorasElegidas(disponibles);
            }
            Integer horasElegidas = postulacion.getHorasElegidas();
            if (horasElegidas == null || horasElegidas < 1) {
                throw new IllegalArgumentException("Indique la cantidad de horas tomadas (mínimo 1).");
            }
            if (disponibles != null && horasElegidas > disponibles) {
                throw new IllegalArgumentException("Solo hay " + disponibles + " horas disponibles en el concurso.");
            }
            postulacion.setConcurso(concurso);
            postulacion.setDocente(docente);
            postulacion.setFechaEmision(LocalDate.now());
            postulacion.setGanador(false);
            postulacion.setEstadoPostulacion(EstadoPostulacion.PENDIENTE_CERTIFICADOS);
            concursoDocenteService.alta(postulacion);
            attributes.addFlashAttribute("msgExito", "Toma de horas registrada: " + horasElegidas + " h reservadas para "
                    + docente.getApellido() + ", " + docente.getNombre() + ". Estado: pendiente de certificados.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/" + concursoId;
    }

    @GetMapping("/{concursoId}/postulaciones/{postulacionId}/horas")
    public String mostrarFormularioHoras(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                         Model model, RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validarModificacionHoras(concurso, postulacion);
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            Integer actuales = horasEfectivas(postulacion);
            Integer disponibles = horasDisponiblesDe(concurso, postulantes);
            model.addAttribute("concurso", concurso);
            model.addAttribute("postulacion", postulacion);
            model.addAttribute("horasActuales", actuales == null ? 0 : actuales);
            model.addAttribute("horasDisponibles", disponibles);
            model.addAttribute("horasMaximas", disponibles == null || actuales == null ? null : actuales + disponibles);
            model.addAttribute("estadoPostulacion", estadoEfectivo(postulacion));
            return "concursos/modificar-horas";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
    }

    @PostMapping("/{concursoId}/postulaciones/{postulacionId}/horas")
    public String modificarHoras(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                 @RequestParam(value = "horasNuevas", required = false) Integer horasNuevas,
                                 RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validarModificacionHoras(concurso, postulacion);
            if (horasNuevas == null || horasNuevas < 1) {
                throw new IllegalArgumentException("Indique una cantidad de horas válida (mínimo 1).");
            }
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            Integer disponibles = horasDisponiblesDe(concurso, postulantes);
            Integer actuales = horasEfectivas(postulacion);
            if (disponibles != null) {
                int maximo = (actuales == null ? 0 : actuales) + disponibles;
                if (horasNuevas > maximo) {
                    throw new IllegalArgumentException("El máximo permitido es " + maximo
                            + " h (horas actuales del docente + horas disponibles del concurso).");
                }
            }
            // Persistir las horas en el Servidor.
            postulacion.setHorasElegidas(horasNuevas);
            concursoDocenteService.modificar(postulacion.getId(), postulacion);
            Integer nuevasDisponibles = horasDisponiblesDe(concurso, postulantesDelConcurso(concursoId));
            attributes.addFlashAttribute("msgExito", "Horas actualizadas a " + horasNuevas + " h. Horas disponibles ahora: "
                    + (nuevasDisponibles == null ? "—" : nuevasDisponibles + " h") + ".");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/" + concursoId;
    }

    // ------------------------------------------------------------------
    // Paso 2: validar certificados
    // ------------------------------------------------------------------

    @GetMapping("/{concursoId}/postulaciones/{postulacionId}/certificados")
    public String mostrarFormularioCertificados(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                                Model model, RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validacionCertificadosService.validarPendiente(concurso, postulacion);

            List<CertificadoDTO> certificados = certificadoService.listarPorPostulacion(postulacionId);
            CertificadoDTO nuevo = new CertificadoDTO();
            nuevo.setTipoCertificado(new TipoCertificadoDTO());

            model.addAttribute("concurso", concurso);
            model.addAttribute("postulacion", postulacion);
            model.addAttribute("horasPostulacion", horasEfectivas(postulacion));
            model.addAttribute("certificados", certificados);
            model.addAttribute("obligatoriosFaltantes", validacionCertificadosService.obligatoriosFaltantes(certificados));
            model.addAttribute("tiposCertificado", tipoCertificadoService.listarActivos());
            model.addAttribute("item", nuevo);
            return "concursos/validar-certificados";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
    }

    @PostMapping("/{concursoId}/postulaciones/{postulacionId}/certificados")
    public String validarCertificados(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                      @RequestParam(value = "decision", required = false) String decision,
                                      @RequestParam(value = "observacion", required = false) String observacion,
                                      RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            String mensaje = validacionCertificadosService.resolver(concurso, postulacion, decision, observacion);
            attributes.addFlashAttribute("msgExito", mensaje);
            return "redirect:/concursos/" + concursoId;
        } catch (RuntimeException e) {
            // Se vuelve al paso 2 (no al concurso) para que el usuario pueda cargar lo que falta.
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId + "/postulaciones/" + postulacionId + "/certificados";
        }
    }

    // ------------------------------------------------------------------
    // Paso 3: confirmación de toma de posesión / declarar inefectivo
    // ------------------------------------------------------------------

    @GetMapping("/{concursoId}/postulaciones/{postulacionId}/posesion")
    public String mostrarFormularioPosesion(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                            Model model, RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validarAbierto(concurso, "no se puede confirmar la toma de posesión");
            if (estadoEfectivo(postulacion) != EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                throw new IllegalArgumentException("La postulación no está pendiente de toma de posesión.");
            }
            LocalDate hoy = LocalDate.now();
            DesignacionDTO designacion = new DesignacionDTO();
            designacion.setDocente(postulacion.getDocente());
            designacion.setFechaInicioEfectiva(hoy);
            designacion.setFechaFinDefinitiva(hoy.plusYears(1));
            model.addAttribute("concurso", concurso);
            model.addAttribute("postulacion", postulacion);
            model.addAttribute("horasPostulacion", horasEfectivas(postulacion));
            model.addAttribute("item", designacion);
            return "concursos/posesion";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
    }

    @PostMapping("/{concursoId}/postulaciones/{postulacionId}/posesion")
    public String confirmarPosesion(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                    @ModelAttribute("item") DesignacionDTO designacion,
                                    RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validarAbierto(concurso, "no se puede confirmar la toma de posesión");
            if (estadoEfectivo(postulacion) != EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                throw new IllegalArgumentException("La postulación no está pendiente de toma de posesión.");
            }
            if (designacion.getFechaInicioEfectiva() == null || designacion.getFechaFinDefinitiva() == null) {
                throw new IllegalArgumentException("Complete las fechas de inicio efectiva y de fin definitiva.");
            }
            if (concurso.getCaracterDesignacion() == null) {
                throw new IllegalArgumentException("El concurso no tiene un carácter de designación definido. Complete el carácter del concurso antes de confirmar la toma de posesión.");
            }
            designacion.setDocente(postulacion.getDocente());
            designacion.setCaracterDesignacion(concurso.getCaracterDesignacion());
            designacion.setEstadoDesignacion(EstadoDesignacion.ACTIVO);
            designacion.setOrigenVacancia(origenVacanciaDelConcurso(concurso));
            designacion.setMotivoVacancia(concurso.getMotivoVacancia());
            // El docente reemplazado tambien se guarda en la designacion para que se vea en el detalle del docente.
            designacion.setProfesorReemplazante(concurso.getProfesorReemplazante());
            designacion.setConcursoId(concursoId);

            CargoDTO cargo = concurso.getCargo() != null ? cargoService.obtener(concurso.getCargo().getId()) : null;
            validarDesignacionVigente(cargo, designacion.getDocente());

            DesignacionDTO nuevaDesignacion = designacionService.alta(designacion);

            if (cargo != null && nuevaDesignacion != null) {
                if (cargo.getDesignaciones() == null) {
                    cargo.setDesignaciones(new ArrayList<>());
                }
                cargo.getDesignaciones().add(nuevaDesignacion);
                cargoService.modificar(cargo.getId(), cargo);
            }

            postulacion.setGanador(true);
            postulacion.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);
            concursoDocenteService.modificar(postulacion.getId(), postulacion);

            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            if (esListoParaCierre(concurso, postulantes)) {
                concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
                service.modificar(concurso.getId(), concurso);
                attributes.addFlashAttribute("msgExito", "Toma de posesión confirmada. El concurso se cerró automáticamente: todas sus horas quedaron con designación definitiva.");
            } else {
                attributes.addFlashAttribute("msgExito", "Toma de posesión confirmada: la designación quedó activa (designación definitiva).");
            }
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/" + concursoId;
    }

    @GetMapping("/{concursoId}/postulaciones/{postulacionId}/inefectivo")
    public String mostrarFormularioInefectivo(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                              Model model, RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validarAbierto(concurso, "no se puede declarar inefectivo");
            if (estadoEfectivo(postulacion) != EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                throw new IllegalArgumentException("Solo se puede declarar inefectivo a docentes pendientes de toma de posesión.");
            }
            model.addAttribute("concurso", concurso);
            model.addAttribute("postulacion", postulacion);
            model.addAttribute("horasPostulacion", horasEfectivas(postulacion));
            return "concursos/inefectivo";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
    }

    @PostMapping("/{concursoId}/postulaciones/{postulacionId}/inefectivo")
    public String declararInefectivo(@PathVariable Long concursoId, @PathVariable Long postulacionId,
                                     @RequestParam(value = "observacion", required = false) String observacion,
                                     RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            ConcursoDocenteDTO postulacion = obtenerPostulacion(concursoId, postulacionId);
            validarAbierto(concurso, "no se puede declarar inefectivo");
            if (estadoEfectivo(postulacion) != EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                throw new IllegalArgumentException("Solo se puede declarar inefectivo a docentes pendientes de toma de posesión.");
            }
            postulacion.setEstadoPostulacion(EstadoPostulacion.CANCELADO_INEFECTIVO);
            postulacion.setObservacion(observacion != null && !observacion.isBlank()
                    ? observacion.trim()
                    : "No se presentó a la toma de posesión.");
            concursoDocenteService.modificar(postulacion.getId(), postulacion);
            Integer liberadas = horasEfectivas(postulacion);
            attributes.addFlashAttribute("msgExito", "Se declaró inefectivo al docente"
                    + (liberadas != null ? "; " + liberadas + " h volvieron a estar disponibles." : "."));
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/" + concursoId;
    }

    // ------------------------------------------------------------------
    // Paso 4: cierre y rellamado
    // ------------------------------------------------------------------

    @PostMapping("/{concursoId}/cerrar")
    public String cerrarConcurso(@PathVariable Long concursoId, RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            if (esCerrado(concurso)) {
                throw new IllegalArgumentException("El concurso ya está cerrado.");
            }
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            long pendientesValidacion = postulantes.stream()
                    .filter(postulacion -> estadoEfectivo(postulacion) == EstadoPostulacion.PENDIENTE_CERTIFICADOS)
                    .count();
            if (pendientesValidacion > 0) {
                throw new IllegalArgumentException("No se puede cerrar el concurso: hay " + pendientesValidacion
                        + " docente(s) pendientes de validación de certificados.");
            }
            concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
            service.modificar(concurso.getId(), concurso);
            Integer disponibles = horasDisponiblesDe(concurso, postulantes);
            if (disponibles != null && disponibles > 0) {
                attributes.addFlashAttribute("msgInfo", "El concurso quedó cerrado con " + disponibles
                        + " h libres. Puede abrir un nuevo concurso asociado (rellamado).");
            } else {
                attributes.addFlashAttribute("msgExito", "El concurso fue cerrado.");
            }
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/" + concursoId;
    }

    @GetMapping("/{concursoId}/rellamado")
    public String mostrarFormularioRellamado(@PathVariable Long concursoId, Model model,
                                             RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            if (!esCerrado(concurso)) {
                throw new IllegalArgumentException("Cierre primero el concurso para generar el rellamado con las horas remanentes.");
            }
            Integer disponibles = horasDisponiblesDe(concurso, postulantesDelConcurso(concursoId));
            if (disponibles == null || disponibles <= 0) {
                throw new IllegalArgumentException("No hay horas remanentes para un rellamado.");
            }
            if (cargoTieneConcursoAbierto(concurso.getCargo() != null ? concurso.getCargo().getId() : null, concursoId)) {
                throw new IllegalArgumentException(CARGO_CONCURSO_ABIERTO);
            }
            ConcursoDTO rellamado = new ConcursoDTO();
            rellamado.setDescripcion("Rellamado – " + concurso.getDescripcion());
            rellamado.setFechaApertura(LocalDate.now());
            rellamado.setHorasTotales(disponibles);
            rellamado.setCargo(concurso.getCargo());
            rellamado.setCaracterDesignacion(concurso.getCaracterDesignacion());
            rellamado.setEstadoConcurso(EstadoConcurso.PUBLICADO);
            rellamado.setEsRellamado(true);
            rellamado.setConcursoOrigenId(concurso.getId());
            model.addAttribute("concurso", concurso);
            model.addAttribute("horasRemanentes", disponibles);
            model.addAttribute("item", rellamado);
            return "concursos/rellamado";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
    }

    @PostMapping("/{concursoId}/rellamado")
    public String crearRellamado(@PathVariable Long concursoId,
                                 @ModelAttribute("item") ConcursoDTO rellamado,
                                 RedirectAttributes attributes) {
        Long nuevoId = null;
        try {
            ConcursoDTO origen = obtenerConcurso(concursoId);
            if (!esCerrado(origen)) {
                throw new IllegalArgumentException("Cierre primero el concurso para generar el rellamado con las horas remanentes.");
            }
            Integer disponibles = horasDisponiblesDe(origen, postulantesDelConcurso(concursoId));
            if (disponibles == null || disponibles <= 0) {
                throw new IllegalArgumentException("No hay horas remanentes para un rellamado.");
            }
            if (cargoTieneConcursoAbierto(origen.getCargo() != null ? origen.getCargo().getId() : null, concursoId)) {
                throw new IllegalArgumentException(CARGO_CONCURSO_ABIERTO);
            }
            if (rellamado.getDescripcion() == null || rellamado.getDescripcion().isBlank()) {
                rellamado.setDescripcion("Rellamado – " + origen.getDescripcion());
            }
            if (rellamado.getFechaConcurso() == null) {
                throw new IllegalArgumentException("Indique la fecha y hora del concurso.");
            }
            if (rellamado.getFechaApertura() == null) {
                rellamado.setFechaApertura(LocalDate.now());
            }
            rellamado.setId(null);
            rellamado.setCargo(origen.getCargo());
            // El carácter no se selecciona en el formulario: el rellamado siempre conserva el del concurso origen.
            rellamado.setCaracterDesignacion(origen.getCaracterDesignacion());
            rellamado.setHorasTotales(disponibles);
            rellamado.setEstadoConcurso(EstadoConcurso.PUBLICADO);
            rellamado.setEsRellamado(true);
            rellamado.setConcursoOrigenId(origen.getId());
            // El rellamado cubre la misma vacancia: hereda el origen capturado en el concurso original.
            rellamado.setOrigenVacancia(origen.getOrigenVacancia());
            rellamado.setMotivoVacancia(origen.getMotivoVacancia() != null
                    ? origen.getMotivoVacancia()
                    : MotivoVacancia.desdeTexto(origen.getOrigenVacancia()));
            rellamado.setProfesorReemplazante(origen.getProfesorReemplazante());
            if (rellamado.getFechaConcurso() != null) {
                rellamado.setFechaCierre(rellamado.getFechaConcurso().toLocalDate());
            }
            ConcursoDTO creado = service.alta(rellamado);
            nuevoId = creado != null ? creado.getId() : null;
            // Las horas remanentes quedan transferidas: el origen cierra definitivamente sin disponibles.
            origen.setHorasTransferidas(disponibles);
            service.modificar(origen.getId(), origen);
            attributes.addFlashAttribute("msgExito", "Se creó el concurso de rellamado con " + disponibles
                    + " h. El concurso original quedó cerrado definitivamente.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/" + concursoId;
        }
        return nuevoId != null ? "redirect:/concursos/" + nuevoId : "redirect:/concursos/" + concursoId;
    }

    // ------------------------------------------------------------------
    // Helpers del flujo
    // ------------------------------------------------------------------

    /**
     * Origen de vacancia que hereda una designación creada desde el concurso: el capturado en el
     * concurso o, para concursos anteriores al campo, el formato histórico "Concurso: <descripción>".
     */
    private String origenVacanciaDelConcurso(ConcursoDTO concurso) {
        String origen = concurso.getOrigenVacancia();
        if (origen != null && !origen.isBlank()) {
            return origen;
        }
        return "Concurso: " + concurso.getDescripcion();
    }

    /**
     * Valida el motivo elegido y arma el texto que se persiste en origenVacancia: la etiqueta
     * del motivo más su detalle opcional, o el texto libre cuando el motivo es "Otro".
     */
    private void aplicarMotivoVacancia(ConcursoDTO concurso) {
        if (concurso.getMotivoVacancia() == null) {
            throw new IllegalArgumentException("Seleccione el motivo de la vacancia del concurso.");
        }
        if (concurso.getMotivoVacancia() == MotivoVacancia.OTRO && concurso.getOrigenVacancia() == null) {
            throw new IllegalArgumentException("Escriba el motivo de la vacancia del concurso.");
        }
        concurso.setOrigenVacancia(MotivoVacancia.texto(concurso.getMotivoVacancia(), concurso.getOrigenVacancia()));
    }

    private ConcursoDTO obtenerConcurso(Long concursoId) {
        ConcursoDTO concurso = service.obtener(concursoId);
        if (concurso == null) {
            throw new IllegalArgumentException("No se encontró el concurso.");
        }
        aplicarTransicionEstado(concurso);
        return concurso;
    }

    private ConcursoDocenteDTO obtenerPostulacion(Long concursoId, Long postulacionId) {
        return postulantesDelConcurso(concursoId).stream()
                .filter(postulacion -> Objects.equals(postulacion.getId(), postulacionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La postulación no pertenece a este concurso."));
    }

    private void validarAbierto(ConcursoDTO concurso, String accion) {
        if (esCerrado(concurso)) {
            throw new IllegalArgumentException("El concurso está cerrado: " + accion + ".");
        }
    }

    private void validarModificacionHoras(ConcursoDTO concurso, ConcursoDocenteDTO postulacion) {
        if (esCerrado(concurso)) {
            throw new IllegalArgumentException("El concurso está cerrado: no se pueden modificar horas.");
        }
        if (esDePreceptor(concurso)) {
            throw new IllegalArgumentException("En un concurso de preceptor no se modifican horas: el preceptor toma todas las horas del concurso.");
        }
        EstadoPostulacion estado = estadoEfectivo(postulacion);
        if (estado == EstadoPostulacion.CANCELADO_INEFECTIVO || estado == EstadoPostulacion.DESIGNACION_DEFINITIVA) {
            throw new IllegalArgumentException("Solo se pueden modificar horas de postulaciones pendientes de certificados o de posesión.");
        }
    }

    /** Un docente solo puede tener una designación vigente (activa o pendiente) por cargo. */
    private void validarDesignacionVigente(CargoDTO cargo, DocenteDTO docente) {
        if (cargo == null || cargo.getDesignaciones() == null || docente == null) {
            return;
        }
        boolean vigente = cargo.getDesignaciones().stream()
                .anyMatch(designacion -> designacion.getDocente() != null
                        && Objects.equals(designacion.getDocente().getId(), docente.getId())
                        && (designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO
                        || designacion.getEstadoDesignacion() == EstadoDesignacion.PENDIENTE));
        if (vigente) {
            throw new IllegalArgumentException("El docente ya posee una designación vigente para este cargo.");
        }
    }

    private boolean esCerrado(ConcursoDTO concurso) {
        return flujoService.esCerrado(concurso);
    }

    /**
     * Cargos que se ofrecen en el formulario: excluye los que ya tienen un concurso abierto
     * (estado distinto de FINALIZADO) distinto del que se está editando. El cargo del concurso
     * actual se conserva aunque aparezca duplicado en registros históricos, para no perderlo al
     * guardar. Si el Servidor no responde, no se restringe (la regla se valida igual al guardar).
     */
    private List<CargoDTO> cargosDisponibles(ConcursoDTO actual) {
        List<CargoDTO> cargos;
        try {
            cargos = cargoService.listarActivos();
        } catch (RuntimeException e) {
            return List.of();
        }
        List<Long> ocupados = concursosAbiertos(actual != null ? actual.getId() : null).stream()
                .map(concurso -> concurso.getCargo() != null ? concurso.getCargo().getId() : null)
                .filter(Objects::nonNull)
                .toList();
        List<CargoDTO> disponibles = new ArrayList<>(cargos.stream()
                .filter(cargo -> !ocupados.contains(cargo.getId()))
                .toList());
        if (actual != null && actual.getCargo() != null
                && disponibles.stream().noneMatch(cargo -> Objects.equals(cargo.getId(), actual.getCargo().getId()))) {
            disponibles.add(actual.getCargo());
        }
        return disponibles;
    }

    /** Concursos abiertos: manda el estado (distinto de FINALIZADO), no las fechas. */
    private List<ConcursoDTO> concursosAbiertos(Long concursoExcluido) {
        try {
            return service.listarActivos().stream()
                    .filter(concurso -> !esCerrado(concurso))
                    .filter(concurso -> !Objects.equals(concurso.getId(), concursoExcluido))
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private boolean cargoTieneConcursoAbierto(Long cargoId, Long concursoExcluido) {
        if (cargoId == null) {
            return false;
        }
        return concursosAbiertos(concursoExcluido).stream()
                .anyMatch(concurso -> concurso.getCargo() != null
                        && Objects.equals(concurso.getCargo().getId(), cargoId));
    }

    /**
     * Regla de estado: un concurso PUBLICADO pasa a EN_PROCESO cuando llega la fecha del
     * concurso (persistida como fechaCierre). El cambio se persiste al primer acceso; si la
     * persistencia falla, se refleja igual en pantalla y se reintenta en el próximo acceso.
     */
    private void aplicarTransicionEstado(ConcursoDTO concurso) {
        if (concurso == null || concurso.getEstadoConcurso() != EstadoConcurso.PUBLICADO
                || concurso.getFechaCierre() == null || concurso.getFechaCierre().isAfter(LocalDate.now())) {
            return;
        }
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        try {
            service.modificar(concurso.getId(), concurso);
        } catch (RuntimeException ignored) {
            // El estado calculado ya quedó reflejado en memoria.
        }
    }

    /** Estado efectivo de una postulación: DTO persistido -> derivado del ganador (registros históricos). */
    private EstadoPostulacion estadoEfectivo(ConcursoDocenteDTO postulacion) {
        return flujoService.estadoEfectivo(postulacion);
    }

    /** Horas efectivas de una postulación (persistidas en el Servidor). */
    private Integer horasEfectivas(ConcursoDocenteDTO postulacion) {
        return flujoService.horasEfectivas(postulacion);
    }

    /** Horas totales del concurso: DTO persistido -> carga horaria del cargo (respaldo). */
    private Integer horasTotalesDe(ConcursoDTO concurso) {
        return flujoService.horasTotalesDe(concurso);
    }

    private int horasAsignadas(List<ConcursoDocenteDTO> postulaciones) {
        return flujoService.horasAsignadas(postulaciones);
    }

    /** Horas disponibles: total − asignadas − transferidas a un rellamado, nunca menor a 0. */
    private Integer horasDisponiblesDe(ConcursoDTO concurso, List<ConcursoDocenteDTO> postulaciones) {
        return flujoService.horasDisponiblesDe(concurso, postulaciones);
    }

    private boolean esListoParaCierre(ConcursoDTO concurso, List<ConcursoDocenteDTO> postulaciones) {
        return flujoService.esListoParaCierre(concurso, postulaciones);
    }

    /** Un cargo de preceptor admite una sola persona (preceptor) por concurso, que toma todas las horas. */
    private boolean esDePreceptor(ConcursoDTO concurso) {
        return concurso.getCargo() != null && concurso.getCargo().getTipoCargo() == TipoCargo.PRECEPTOR;
    }

    /** Concurso de preceptor que ya tiene una postulación vigente (las canceladas liberan el lugar). */
    private boolean preceptorCubierto(ConcursoDTO concurso, List<ConcursoDocenteDTO> postulantes) {
        return esDePreceptor(concurso) && postulantes.stream()
                .anyMatch(postulacion -> !estadoEfectivo(postulacion).isCancelado());
    }

    private List<DocenteDTO> docentesDisponibles(List<ConcursoDocenteDTO> postulantes) {
        List<Long> ocupados = postulantes.stream()
                .filter(postulacion -> !estadoEfectivo(postulacion).isCancelado())
                .map(ConcursoDocenteDTO::getDocente)
                .filter(Objects::nonNull)
                .map(DocenteDTO::getId)
                .toList();
        return docenteService.listarActivos().stream()
                .filter(docente -> !ocupados.contains(docente.getId()))
                .toList();
    }

    /**
     * Docentes habilitados para postular: excluye a quienes tienen una designación efectiva vigente
     * en el mismo cargo del concurso —y por lo tanto misma materia y curso— (regla general), y en
     * rellamados también a quienes obtuvieron designación efectiva en el concurso origen.
     */
    private List<DocenteDTO> docentesDisponiblesParaPostular(ConcursoDTO concurso, List<ConcursoDocenteDTO> postulantes) {
        if (preceptorCubierto(concurso, postulantes)) {
            return List.of();
        }
        TipoCargo tipoCargo = esDePreceptor(concurso) ? TipoCargo.PRECEPTOR : TipoCargo.DOCENTE;
        List<DocenteDTO> disponibles = docentesDisponibles(postulantes).stream()
                .filter(docente -> (docente.getTipo() == null ? TipoCargo.DOCENTE : docente.getTipo()) == tipoCargo)
                .toList();
        List<DocenteDTO> excluidosDocentes = new ArrayList<>(docentesConDesignacionActivaDelCargo(concurso));
        excluidosDocentes.addAll(docentesConDesignacionEfectivaDelOrigen(concurso));
        List<Long> excluidos = excluidosDocentes.stream()
                .map(DocenteDTO::getId)
                .distinct()
                .toList();
        if (excluidos.isEmpty()) {
            return disponibles;
        }
        return disponibles.stream()
                .filter(docente -> !excluidos.contains(docente.getId()))
                .toList();
    }

    /**
     * Regla general: un docente con una designación efectiva (ACTIVA) vigente en el mismo cargo del
     * concurso —y por lo tanto misma materia y curso— está siendo reemplazado: no puede postularse
     * a su propio reemplazo y no se ofrece en el selector. Devuelve vacío si el concurso no tiene
     * cargo o no se pueden resolver las designaciones, para no alterar el resto de los casos.
     */
    private List<DocenteDTO> docentesConDesignacionActivaDelCargo(ConcursoDTO concurso) {
        if (concurso.getCargo() == null || concurso.getCargo().getId() == null) {
            return List.of();
        }
        CargoDTO cargo;
        try {
            cargo = cargoService.obtener(concurso.getCargo().getId());
        } catch (RuntimeException e) {
            return List.of();
        }
        if (cargo == null || cargo.getDesignaciones() == null) {
            return List.of();
        }
        return cargo.getDesignaciones().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .map(DesignacionDTO::getDocente)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Regla de rellamados: un docente que ya obtuvo una designación efectiva (ACTIVA) en el concurso
     * origen para el mismo cargo —y por lo tanto misma materia y curso— no puede volver a tomar horas
     * en el rellamado. El vínculo rellamado->origen se lee del DTO persistido en el Servidor.
     * Devuelve vacío si el concurso no es un rellamado o si no se puede
     * determinar el origen, para no alterar el resto de los casos.
     */
    private List<DocenteDTO> docentesConDesignacionEfectivaDelOrigen(ConcursoDTO concurso) {
        Boolean esRellamado = concurso.getEsRellamado();
        Long concursoOrigenId = concurso.getConcursoOrigenId();
        if (!Boolean.TRUE.equals(esRellamado) || concursoOrigenId == null || concurso.getCargo() == null) {
            return List.of();
        }
        ConcursoDTO origen;
        CargoDTO cargo;
        try {
            origen = obtenerConcurso(concursoOrigenId);
            cargo = cargoService.obtener(concurso.getCargo().getId());
        } catch (RuntimeException e) {
            return List.of();
        }
        if (cargo == null || cargo.getDesignaciones() == null) {
            return List.of();
        }
        // Las designaciones nuevas se reconocen por su vínculo directo con el concurso; las
        // históricas conservan la comparación por el texto "Concurso: <descripción>".
        String origenEsperado = origen.getDescripcion() != null ? "Concurso: " + origen.getDescripcion() : null;
        return cargo.getDesignaciones().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .filter(designacion -> designacion.getConcursoId() != null
                        ? Objects.equals(designacion.getConcursoId(), origen.getId())
                        : (origenEsperado != null && origenEsperado.equals(designacion.getOrigenVacancia())))
                .map(DesignacionDTO::getDocente)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<ConcursoDocenteDTO> postulantesDelConcurso(Long concursoId) {
        return flujoService.postulantesDelConcurso(concursoId);
    }
}