package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.enums.EstadoConcurso;
import com.elvarest.cliente.dto.enums.EstadoPostulacion;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.DesignacionService;
import com.elvarest.cliente.services.DocenteService;
import com.elvarest.cliente.services.FlujoConcursoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/docentes")
public class DocenteController extends BaseController<DocenteDTO, Long> {

    private final CertificadoService certificadoService;
    private final ConcursoDocenteService concursoDocenteService;
    private final DesignacionService designacionService;
    private final FlujoConcursoService flujoService;
    private final CargoService cargoService;

    public DocenteController(DocenteService service, CertificadoService certificadoService,
                             ConcursoDocenteService concursoDocenteService, DesignacionService designacionService,
                             FlujoConcursoService flujoService, CargoService cargoService) {
        super(service);
        this.certificadoService = certificadoService;
        this.concursoDocenteService = concursoDocenteService;
        this.designacionService = designacionService;
        this.flujoService = flujoService;
        this.cargoService = cargoService;
        initController(new DocenteDTO(), "Lista de docentes", "Editar docente", "docentes/");
    }

    @Override
    protected void postConsulta(DocenteDTO docente) throws ErrorServiceException {
        model.addAttribute("certificados", certificadoService.listarActivos().stream()
                .filter(certificado -> certificado.getDocente() != null
                        && Objects.equals(certificado.getDocente().getId(), docente.getId()))
                .toList());

        // Historial: solo postulaciones con designación definitiva (cualquier estado del concurso)
        // o todavía pendientes de concretarla en concursos donde eso sigue siendo posible, más reciente primero.
        List<ConcursoDocenteDTO> historial = concursoDocenteService.listarActivos().stream()
                .filter(postulacion -> postulacion.getDocente() != null
                        && Objects.equals(postulacion.getDocente().getId(), docente.getId()))
                .filter(this::visibleEnHistorial)
                .sorted(Comparator.comparing(ConcursoDocenteDTO::getFechaEmision,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        model.addAttribute("historialPostulaciones", historial);

        Map<Long, EstadoPostulacion> estadosHistorial = new HashMap<>();
        Map<Long, Integer> horasHistorial = new HashMap<>();
        for (ConcursoDocenteDTO postulacion : historial) {
            if (postulacion.getId() != null) {
                estadosHistorial.put(postulacion.getId(), flujoService.estadoEfectivo(postulacion));
                horasHistorial.put(postulacion.getId(), flujoService.horasEfectivas(postulacion));
            }
        }
        model.addAttribute("estadosHistorial", estadosHistorial);
        model.addAttribute("horasHistorial", horasHistorial);

        // Designaciones registradas del docente.
        model.addAttribute("designacionesDocente", designacionService.listarActivos().stream()
                .filter(designacion -> designacion.getDocente() != null
                        && Objects.equals(designacion.getDocente().getId(), docente.getId()))
                .toList());

        // Materia/Curso de cada designación, resuelta desde su vínculo con el cargo
        // (mismo formato que el listado de cargos). Las designaciones manuales sin
        // cargo vinculado no aparecen en el mapa y la vista muestra "—".
        Map<Long, String> materiasDesignaciones = new HashMap<>();
        for (CargoDTO cargo : cargoService.listarActivos()) {
            if (cargo.getDesignaciones() == null) {
                continue;
            }
            String materia = cargo.getMateria() != null
                    ? cargo.getMateria().getNombre() + (cargo.getMateria().getCurso() != null
                            ? " · " + cargo.getMateria().getCurso().getAnio() + "° Año"
                            : "")
                    : "Sin materia asignada";
            for (DesignacionDTO designacion : cargo.getDesignaciones()) {
                if (designacion.getId() != null) {
                    materiasDesignaciones.put(designacion.getId(), materia);
                }
            }
        }
        model.addAttribute("materiasDesignaciones", materiasDesignaciones);
    }

    /**
     * Historial: se incluyen las postulaciones con designación definitiva (sin importar el
     * estado actual del concurso) y las pendientes de concretarla en concursos donde todavía
     * es posible (PUBLICADO o EN_PROCESO). Las participaciones canceladas o de concursos
     * finalizados sin designación definitiva no se muestran.
     */
    private boolean visibleEnHistorial(ConcursoDocenteDTO postulacion) {
        EstadoPostulacion estado = flujoService.estadoEfectivo(postulacion);
        if (estado == EstadoPostulacion.DESIGNACION_DEFINITIVA) {
            return true;
        }
        if (estado.isCancelado() || postulacion.getConcurso() == null) {
            return false;
        }
        EstadoConcurso estadoConcurso = postulacion.getConcurso().getEstadoConcurso();
        return estadoConcurso == EstadoConcurso.PUBLICADO || estadoConcurso == EstadoConcurso.EN_PROCESO;
    }
}
