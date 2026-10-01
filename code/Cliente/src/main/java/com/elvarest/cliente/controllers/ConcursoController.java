package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.enums.EstadoConcurso;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.ConcursoService;
import com.elvarest.cliente.services.DesignacionService;
import com.elvarest.cliente.services.DocenteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/concursos")
public class ConcursoController extends BaseController<ConcursoDTO, Long> {

    private final ConcursoDocenteService concursoDocenteService;
    private final DocenteService docenteService;
    private final CargoService cargoService;
    private final DesignacionService designacionService;

    public ConcursoController(ConcursoService service, ConcursoDocenteService concursoDocenteService,
                              DocenteService docenteService, CargoService cargoService,
                              DesignacionService designacionService) {
        super(service);
        this.concursoDocenteService = concursoDocenteService;
        this.docenteService = docenteService;
        this.cargoService = cargoService;
        this.designacionService = designacionService;
        initController(new ConcursoDTO(), "Lista de concursos", "Editar concurso", "concursos/");
    }

    @Override
    @GetMapping("/list")
    public String listar(Model model) {
        try {
            List<ConcursoDTO> concursos = service.listarActivos();
            Map<Long, Integer> cantidadPostulantes = new HashMap<>();
            Map<Long, ConcursoDocenteDTO> ganadores = new HashMap<>();
            concursoDocenteService.listarActivos().forEach(postulacion -> {
                if (postulacion.getConcurso() == null || postulacion.getConcurso().getId() == null) {
                    return;
                }
                Long concursoId = postulacion.getConcurso().getId();
                cantidadPostulantes.merge(concursoId, 1, Integer::sum);
                if (postulacion.isGanador()) {
                    ganadores.put(concursoId, postulacion);
                }
            });
            concursos.forEach(concurso -> cantidadPostulantes.putIfAbsent(concurso.getId(), 0));
            model.addAttribute("items", concursos);
            model.addAttribute("cantidadPostulantes", cantidadPostulantes);
            model.addAttribute("ganadores", ganadores);
            model.addAttribute("titleList", titleList);
            model.addAttribute("nameEntityLower", nameEntityLower);
        } catch (Exception e) {
            model.addAttribute("items", List.of());
            model.addAttribute("cantidadPostulantes", Map.of());
            model.addAttribute("ganadores", Map.of());
            model.addAttribute("msgError", "No se pudo recuperar el listado de concursos: " + e.getMessage());
        }
        return viewList;
    }

    @Override
    protected void postConsulta(ConcursoDTO concurso) {
        List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concurso.getId());
        model.addAttribute("cantidadPostulantes", postulantes.size());
        model.addAttribute("ganador", postulantes.stream()
                .filter(ConcursoDocenteDTO::isGanador)
                .findFirst()
                .orElse(null));
    }

    @GetMapping("/{concursoId}/postular")
    public String mostrarFormularioPostulacion(@PathVariable Long concursoId, Model model,
                                               RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarEnProceso(concurso);
            ConcursoDocenteDTO postulacion = new ConcursoDocenteDTO();
            postulacion.setConcurso(concurso);
            model.addAttribute("item", postulacion);
            model.addAttribute("concurso", concurso);
            model.addAttribute("docentes", docentesDisponibles(concursoId));
            return "concursos/postular";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/list";
        }
    }

    @PostMapping("/{concursoId}/postular")
    public String postular(@PathVariable Long concursoId,
                           @ModelAttribute("item") ConcursoDocenteDTO postulacion,
                           RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarEnProceso(concurso);
            Long docenteId = postulacion.getDocente() == null ? null : postulacion.getDocente().getId();
            if (docenteId == null) {
                throw new IllegalArgumentException("Debe seleccionar un docente.");
            }
            boolean docenteActivo = docenteService.listarActivos().stream()
                    .anyMatch(docente -> Objects.equals(docente.getId(), docenteId));
            if (!docenteActivo) {
                throw new IllegalArgumentException("Solo se pueden postular docentes activos.");
            }
            boolean yaPostulado = concursoDocenteService.listarActivos().stream()
                    .anyMatch(actual -> actual.getConcurso() != null
                            && Objects.equals(actual.getConcurso().getId(), concursoId)
                            && actual.getDocente() != null
                            && Objects.equals(actual.getDocente().getId(), docenteId));
            if (yaPostulado) {
                throw new IllegalArgumentException("El docente ya está postulado a este concurso.");
            }
            DocenteDTO docente = docenteService.obtener(docenteId);
            postulacion.setConcurso(concurso);
            postulacion.setDocente(docente);
            postulacion.setFechaEmision(LocalDate.now());
            postulacion.setGanador(false);
            concursoDocenteService.alta(postulacion);
            attributes.addFlashAttribute("msgExito", "El docente fue postulado correctamente.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/" + concursoId;
    }

    @GetMapping("/{concursoId}/ganador")
    public String mostrarFormularioGanador(@PathVariable Long concursoId, Model model,
                                           RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarFinalizado(concurso);
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            if (postulantes.isEmpty()) {
                throw new IllegalArgumentException("El concurso no tiene postulantes para seleccionar como ganador.");
            }
            model.addAttribute("concurso", concurso);
            model.addAttribute("postulantes", postulantes);
            return "concursos/ganador";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/list";
        }
    }

    @PostMapping("/{concursoId}/ganador")
    public String seleccionarGanador(@PathVariable Long concursoId,
                                     @RequestParam Long postulacionId,
                                     RedirectAttributes attributes) {
        try {
            validarFinalizado(obtenerConcurso(concursoId));
            List<ConcursoDocenteDTO> postulantes = postulantesDelConcurso(concursoId);
            ConcursoDocenteDTO seleccionado = postulantes.stream()
                    .filter(postulacion -> Objects.equals(postulacion.getId(), postulacionId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("El docente seleccionado no está postulado a este concurso."));
            for (ConcursoDocenteDTO postulacion : postulantes) {
                postulacion.setGanador(Objects.equals(postulacion.getId(), seleccionado.getId()));
                concursoDocenteService.modificar(postulacion.getId(), postulacion);
            }
            attributes.addFlashAttribute("msgExito", "Se actualizó el docente ganador del concurso.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/list";
    }

    @GetMapping("/{concursoId}/agregar-cargo")
    public String mostrarFormularioCargo(@PathVariable Long concursoId, Model model,
                                        RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarFinalizado(concurso);
            ConcursoDocenteDTO ganador = ganadorDelConcurso(concursoId);
            if (ganador == null) {
                throw new IllegalArgumentException("Debe seleccionar el docente ganador antes de agregar el cargo.");
            }
            LocalDate hoy = LocalDate.now();
            DesignacionDTO designacion = new DesignacionDTO();
            designacion.setDocente(ganador.getDocente());
            designacion.setFechaInicioEfectiva(hoy);
            designacion.setFechaFinEstimada(hoy.plusYears(1));
            designacion.setOrigenVacancia("Concurso: " + concurso.getDescripcion());
            model.addAttribute("item", designacion);
            model.addAttribute("concurso", concurso);
            model.addAttribute("tipoDesignaciones",
                    com.elvarest.cliente.dto.enums.CaracterDesignacion.values());
            model.addAttribute("estadosDesignacion", EstadoDesignacion.values());
            return "concursos/agregar-cargo";
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            return "redirect:/concursos/list";
        }
    }

    @PostMapping("/{concursoId}/agregar-cargo")
    public String agregarCargo(@PathVariable Long concursoId,
                               @ModelAttribute("item") DesignacionDTO designacion,
                               RedirectAttributes attributes) {
        try {
            ConcursoDTO concurso = obtenerConcurso(concursoId);
            validarFinalizado(concurso);
            ConcursoDocenteDTO ganador = ganadorDelConcurso(concursoId);
            if (ganador == null) {
                throw new IllegalArgumentException("Debe seleccionar el docente ganador antes de agregar el cargo.");
            }
            if (concurso.getCargo() == null) {
                throw new IllegalArgumentException("El concurso no tiene un cargo asociado.");
            }
            if (designacion.getFechaInicioEfectiva() == null || designacion.getFechaFinEstimada() == null
                    || designacion.getCaracterDesignacion() == null) {
                throw new IllegalArgumentException("Complete las fechas y el tipo de cargo.");
            }
            designacion.setDocente(ganador.getDocente());
            designacion.setFechaFinDefinitiva(designacion.getFechaFinEstimada());
            designacion.setEstadoDesignacion(
                    designacion.getEstadoDesignacion() == null ? EstadoDesignacion.PENDIENTE : designacion.getEstadoDesignacion());
            designacion.setOrigenVacancia("Concurso: " + concurso.getDescripcion());
            DesignacionDTO nuevaDesignacion = designacionService.alta(designacion);

            CargoDTO cargo = cargoService.obtener(concurso.getCargo().getId());
            if (cargo == null) {
                throw new IllegalArgumentException("No se encontró el cargo asociado al concurso.");
            }
            if (cargo.getDesignaciones() == null) {
                cargo.setDesignaciones(new ArrayList<>());
            }
            cargo.getDesignaciones().add(nuevaDesignacion);
            cargoService.modificar(cargo.getId(), cargo);
            attributes.addFlashAttribute("msgExito", "El cargo fue agregado al docente ganador.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        }
        return "redirect:/concursos/list";
    }

    private ConcursoDTO obtenerConcurso(Long concursoId) {
        ConcursoDTO concurso = service.obtener(concursoId);
        if (concurso == null) {
            throw new IllegalArgumentException("No se encontró el concurso.");
        }
        return concurso;
    }

    private void validarEnProceso(ConcursoDTO concurso) {
        if (concurso.getEstadoConcurso() != EstadoConcurso.EN_PROCESO) {
            throw new IllegalArgumentException("Solo se pueden postular docentes a concursos en proceso.");
        }
    }

    private void validarFinalizado(ConcursoDTO concurso) {
        if (concurso.getEstadoConcurso() != EstadoConcurso.FINALIZADO) {
            throw new IllegalArgumentException("Esta acción solo está disponible para concursos finalizados.");
        }
    }

    private List<DocenteDTO> docentesDisponibles(Long concursoId) {
        List<Long> postulados = postulantesDelConcurso(concursoId).stream()
                .filter(postulacion -> postulacion.getDocente() != null)
                .map(postulacion -> postulacion.getDocente().getId())
                .toList();
        return docenteService.listarActivos().stream()
                .filter(docente -> !postulados.contains(docente.getId()))
                .toList();
    }

    private List<ConcursoDocenteDTO> postulantesDelConcurso(Long concursoId) {
        return concursoDocenteService.listarActivos().stream()
                .filter(postulacion -> postulacion.getConcurso() != null
                        && Objects.equals(postulacion.getConcurso().getId(), concursoId))
                .toList();
    }

    private ConcursoDocenteDTO ganadorDelConcurso(Long concursoId) {
        return postulantesDelConcurso(concursoId).stream()
                .filter(ConcursoDocenteDTO::isGanador)
                .findFirst()
                .orElse(null);
    }
}
