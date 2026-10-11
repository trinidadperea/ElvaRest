package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.ConcursoService;
import com.elvarest.cliente.services.FlujoConcursoService;
import com.elvarest.cliente.services.MateriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/cargos")
public class CargoController extends BaseController<CargoDTO, Long> {

    private final MateriaService materiaService;
    private final ConcursoService concursoService;
    private final ConcursoDocenteService concursoDocenteService;
    private final FlujoConcursoService flujoService;

    public CargoController(CargoService service, MateriaService materiaService, ConcursoService concursoService,
                           ConcursoDocenteService concursoDocenteService, FlujoConcursoService flujoService) {
        super(service);
        this.materiaService = materiaService;
        this.concursoService = concursoService;
        this.concursoDocenteService = concursoDocenteService;
        this.flujoService = flujoService;
        initController(new CargoDTO(), "Gestión de cargos", "Editar cargo", "cargos/");
    }

    @Override
    protected void preAlta() throws ErrorServiceException {
        model.addAttribute("materias", materiaService.listarActivos());
    }

    @Override
    protected void preModificacion() throws ErrorServiceException {
        model.addAttribute("materias", materiaService.listarActivos());
    }

    @Override
    protected void postConsulta(CargoDTO cargo) throws ErrorServiceException {
        List<DesignacionDTO> designaciones = cargo.getDesignaciones() == null ? List.of()
                : cargo.getDesignaciones().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .sorted(Comparator.comparing(DesignacionDTO::getFechaInicioEfectiva,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        model.addAttribute("designaciones", designaciones);
        model.addAttribute("vacante", esVacante(designaciones));
        model.addAttribute("designacionesActivas", designaciones.stream()
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .count());
        model.addAttribute("horasDesignacion", horasTomadasEnConcursos(cargo, designaciones));
    }

    /** Agrega el estado (vacante/ocupado) y el conteo de designaciones activas de cada cargo al listado. */
    @Override
    @GetMapping("/list")
    public String listar(Model model) {
        String vista = super.listar(model);
        try {
            if (model.getAttribute("items") instanceof List<?> cargos) {
                Map<Long, Boolean> estadosCargos = new HashMap<>();
                Map<Long, Long> designacionesActivas = new HashMap<>();
                for (Object elemento : cargos) {
                    if (elemento instanceof CargoDTO cargo && cargo.getId() != null) {
                        estadosCargos.put(cargo.getId(),
                                esVacante(cargo.getDesignaciones() == null ? List.of() : cargo.getDesignaciones()));
                        designacionesActivas.put(cargo.getId(), cuentaDesignacionesActivas(cargo.getDesignaciones()));
                    }
                }
                model.addAttribute("cargosVacantes", estadosCargos);
                model.addAttribute("cargosDesignacionesActivas", designacionesActivas);
            }
        } catch (Exception ignored) {
            // El listado se muestra igual aunque no se pueda calcular el estado.
        }
        return vista;
    }

    /**
     * Cantidad de designaciones activas de un cargo para el listado: no eliminadas y en estado
     * ACTIVO (mismo criterio que la tarjeta "Designaciones activas" del detalle del cargo).
     */
    private long cuentaDesignacionesActivas(List<DesignacionDTO> designaciones) {
        if (designaciones == null) {
            return 0;
        }
        return designaciones.stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .count();
    }

    /**
     * Al modificar un cargo se conservan sus designaciones originales: el formulario solo
     * edita los datos propios del cargo y el backend reemplaza la entidad completa.
     */
    @Override
    @PostMapping("/actualizar")
    public String actualizar(@ModelAttribute("item") CargoDTO entidad,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             RedirectAttributes attributes, Model model) {
        try {
            this.model = model;
            if (entidad.getId() != null) {
                CargoDTO original = service.obtener(entidad.getId());
                if (original != null) {
                    entidad.setDesignaciones(original.getDesignaciones());
                    entidad.setEliminado(original.getEliminado());
                }
            }
            if (entidad.getId() == null) {
                service.alta(entidad);
            } else {
                service.modificar(entidad.getId(), entidad);
            }
            attributes.addFlashAttribute("msgExito", "La acción fue realizada correctamente.");
            return redirectList;
        } catch (ErrorServiceException e) {
            model.addAttribute("msgError", e.getMessage());
            repoblarFormulario(entidad, model);
            return entidad.getId() == null ? viewCreate : viewEdit;
        } catch (Exception e) {
            model.addAttribute("msgError", "Error de Sistema");
            repoblarFormulario(entidad, model);
            return entidad.getId() == null ? viewCreate : viewEdit;
        }
    }

    private boolean esVacante(List<DesignacionDTO> designaciones) {
        if (designaciones == null || designaciones.isEmpty()) {
            return true;
        }
        return designaciones.stream().noneMatch(designacion ->
                designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO
                        || designacion.getEstadoDesignacion() == EstadoDesignacion.PENDIENTE);
    }

    /**
     * Horas que el docente tomó en el concurso que originó cada designación (se muestran en la
     * columna "Carga horaria" del detalle). Se resuelven del mismo flujo de toma de horas que el
     * resto de la aplicación; si una designación no tiene concurso resoluble (manual o histórica),
     * no se agrega entrada y la vista mantiene la carga horaria del cargo.
     */
    private Map<Long, Integer> horasTomadasEnConcursos(CargoDTO cargo, List<DesignacionDTO> designaciones) {
        Map<Long, Integer> horas = new HashMap<>();
        if (cargo.getId() == null || designaciones.isEmpty()) {
            return horas;
        }
        List<ConcursoDTO> concursos;
        List<ConcursoDocenteDTO> postulaciones;
        try {
            concursos = concursoService.listarActivos();
            postulaciones = concursoDocenteService.listarActivos();
        } catch (RuntimeException e) {
            return horas;
        }
        for (DesignacionDTO designacion : designaciones) {
            Integer tomadas = horasTomadasEnConcurso(designacion, cargo.getId(), concursos, postulaciones);
            if (designacion.getId() != null && tomadas != null) {
                horas.put(designacion.getId(), tomadas);
            }
        }
        return horas;
    }

    /**
     * Horas del docente en el concurso que originó la designación: si la designación tiene vínculo
     * directo con el concurso (concursoId) se resuelve por ese identificador; las designaciones
     * históricas conservan el respaldo por descripción ("Concurso: <descripción>").
     */
    private Integer horasTomadasEnConcurso(DesignacionDTO designacion, Long cargoId,
                                           List<ConcursoDTO> concursos, List<ConcursoDocenteDTO> postulaciones) {
        if (designacion.getDocente() == null) {
            return null;
        }
        List<Long> concursosOrigen;
        if (designacion.getConcursoId() != null) {
            concursosOrigen = concursos.stream()
                    .filter(concurso -> Objects.equals(concurso.getId(), designacion.getConcursoId()))
                    .map(ConcursoDTO::getId)
                    .toList();
        } else if (designacion.getOrigenVacancia() != null
                && designacion.getOrigenVacancia().startsWith("Concurso: ")) {
            String descripcion = designacion.getOrigenVacancia().substring("Concurso: ".length());
            concursosOrigen = concursos.stream()
                    .filter(concurso -> concurso.getCargo() != null
                            && Objects.equals(concurso.getCargo().getId(), cargoId)
                            && descripcion.equals(concurso.getDescripcion()))
                    .map(ConcursoDTO::getId)
                    .toList();
        } else {
            return null;
        }
        if (concursosOrigen.isEmpty()) {
            return null;
        }
        return postulaciones.stream()
                .filter(postulacion -> postulacion.getId() != null && postulacion.getConcurso() != null
                        && postulacion.getDocente() != null
                        && concursosOrigen.contains(postulacion.getConcurso().getId())
                        && Objects.equals(postulacion.getDocente().getId(), designacion.getDocente().getId()))
                .sorted(Comparator.<ConcursoDocenteDTO, Boolean>comparing(ConcursoDocenteDTO::isGanador, Comparator.reverseOrder())
                        .thenComparing(ConcursoDocenteDTO::getId, Comparator.reverseOrder()))
                .map(flujoService::horasEfectivas)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
