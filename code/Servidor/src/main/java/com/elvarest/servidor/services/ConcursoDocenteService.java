package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DocenteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Postulaciones (toma de horas). Las reglas de §3.2 a §3.6 se validan también acá, para que
 * una llamada directa a la API no pueda saltearlas.
 */
@Service
public class ConcursoDocenteService
        extends BaseService<ConcursoDocente, Long> {

    /** Transiciones válidas del ciclo de la postulación (§2). Permanecer en el mismo estado siempre es válido. */
    private static final Map<EstadoPostulacion, Set<EstadoPostulacion>> TRANSICIONES = Map.of(
            EstadoPostulacion.PENDIENTE_CERTIFICADOS,
            Set.of(EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION, EstadoPostulacion.CANCELADO_INEFECTIVO),
            EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION,
            Set.of(EstadoPostulacion.DESIGNACION_DEFINITIVA, EstadoPostulacion.CANCELADO_INEFECTIVO),
            EstadoPostulacion.DESIGNACION_DEFINITIVA, Set.of(),
            EstadoPostulacion.CANCELADO_INEFECTIVO, Set.of());

    private final ConcursoDocenteRepository concursoDocenteRepository;
    private final ConcursoRepository concursoRepository;
    private final DocenteRepository docenteRepository;

    public ConcursoDocenteService(
            ConcursoDocenteRepository concursoDocenteRepository,
            ConcursoRepository concursoRepository,
            DocenteRepository docenteRepository) {

        super(concursoDocenteRepository);
        this.concursoDocenteRepository = concursoDocenteRepository;
        this.concursoRepository = concursoRepository;
        this.docenteRepository = docenteRepository;
    }

    public List<ConcursoDocente> obtenerOrdenMerito(Long concursoId) {
        return concursoDocenteRepository
                .findByConcursoIdOrderByIdAsc(concursoId);
    }

    public boolean docenteYaPostulado(
            Long concursoId,
            Long docenteId) {

        return concursoDocenteRepository
                .existsByConcursoIdAndDocenteId(
                        concursoId,
                        docenteId
                );
    }

    // ------------------------------------------------------------------
    // §3.2 Postulación / toma de horas
    // ------------------------------------------------------------------

    @Override
    protected void preAlta(ConcursoDocente postulacion) throws ErrorServiceException {
        Concurso concurso = concursoDe(postulacion);
        validarAbierto(concurso, "no se pueden registrar tomas de horas");

        Long docenteId = postulacion.getDocente() == null ? null : postulacion.getDocente().getId();
        if (docenteId == null) {
            throw new ErrorServiceException("Debe seleccionar un docente.");
        }
        Docente docente = docenteRepository.findById(docenteId)
                .filter(encontrado -> !Boolean.TRUE.equals(encontrado.getEliminado()) && encontrado.isActivo())
                .orElseThrow(() -> new ErrorServiceException("Solo se pueden registrar docentes activos."));

        boolean esDePreceptor = ReglasConcurso.esDePreceptor(concurso);
        boolean esPreceptor = docente.getTipo() == TipoCargo.PRECEPTOR;
        if (esDePreceptor && !esPreceptor) {
            throw new ErrorServiceException("Este cargo es de preceptor: solo se pueden postular preceptores.");
        }
        if (!esDePreceptor && esPreceptor) {
            throw new ErrorServiceException("Este cargo es docente: no se pueden postular preceptores.");
        }

        List<ConcursoDocente> postulaciones = postulacionesDe(concurso.getId());
        List<ConcursoDocente> vigentes = postulaciones.stream()
                .filter(actual -> !ReglasConcurso.esCancelada(actual))
                .toList();
        if (vigentes.stream().anyMatch(actual -> esDelDocente(actual.getDocente(), docenteId))) {
            throw new ErrorServiceException("El docente ya tiene una toma de horas vigente en este concurso.");
        }
        if (esDePreceptor && !vigentes.isEmpty()) {
            throw new ErrorServiceException("El concurso de preceptor admite una sola persona "
                    + "y ya tiene una postulación vigente.");
        }

        if (tieneDesignacionEfectivaDelOrigen(concurso, docenteId)) {
            throw new ErrorServiceException("El docente ya obtuvo una designación efectiva en el concurso origen "
                    + "para este mismo cargo, materia y curso: no puede volver a seleccionarse en el rellamado.");
        }
        if (tieneDesignacionActivaEnElCargo(concurso, docenteId)) {
            throw new ErrorServiceException("El docente tiene una designación efectiva vigente en este mismo cargo, "
                    + "materia y curso: no puede postularse a su propio reemplazo.");
        }

        if (esDePreceptor) {
            // El preceptor toma todas las horas disponibles, sin importar lo que envíe el cliente.
            Integer disponibles = ReglasConcurso.horasDisponibles(concurso, postulaciones);
            if (disponibles == null || disponibles < 1) {
                throw new ErrorServiceException("No quedan horas disponibles en el concurso.");
            }
            postulacion.setHorasElegidas(disponibles);
        }

        Integer horas = postulacion.getHorasElegidas();
        if (horas == null || horas < 1) {
            throw new ErrorServiceException("Indique la cantidad de horas tomadas (mínimo 1).");
        }
        Integer disponibles = ReglasConcurso.horasDisponibles(concurso, postulaciones);
        if (disponibles != null && horas > disponibles) {
            throw new ErrorServiceException("Solo hay " + disponibles + " horas disponibles en el concurso.");
        }

        // Valores iniciales de toda postulación, sin importar lo que envíe el cliente.
        postulacion.setConcurso(concurso);
        postulacion.setDocente(docente);
        postulacion.setFechaEmision(LocalDate.now());
        postulacion.setGanador(false);
        postulacion.setEstadoPostulacion(EstadoPostulacion.PENDIENTE_CERTIFICADOS);
    }

    // ------------------------------------------------------------------
    // §3.3 a §3.6 Horas y ciclo de estados
    // ------------------------------------------------------------------

    @Override
    protected void preModificacion(ConcursoDocente nueva) throws ErrorServiceException {
        Optional<ConcursoDocente> guardada = concursoDocenteRepository.findById(nueva.getId());
        if (guardada.isEmpty()) {
            return; // BaseService responde "no encontrada".
        }
        ConcursoDocente actual = guardada.get();

        if (nueva.getConcurso() == null || actual.getConcurso() == null
                || !Objects.equals(nueva.getConcurso().getId(), actual.getConcurso().getId())
                || nueva.getDocente() == null || actual.getDocente() == null
                || !Objects.equals(nueva.getDocente().getId(), actual.getDocente().getId())) {
            throw new ErrorServiceException("No se puede cambiar el concurso ni el docente de una postulación.");
        }
        Concurso concurso = concursoDe(actual);

        EstadoPostulacion estadoActual = ReglasConcurso.estadoEfectivo(actual);
        // Los registros históricos sin estado persistido no tienen ciclo que validar (§9):
        // el flujo legacy de "ganador" solo cambia esa marca.
        if (nueva.getEstadoPostulacion() != null && nueva.getEstadoPostulacion() != estadoActual) {
            validarAbierto(concurso, "no se puede cambiar el estado de la postulación");
            if (!TRANSICIONES.get(estadoActual).contains(nueva.getEstadoPostulacion())) {
                throw new ErrorServiceException("No se puede pasar una postulación de «" + ReglasConcurso.etiqueta(estadoActual)
                        + "» a «" + ReglasConcurso.etiqueta(nueva.getEstadoPostulacion()) + "».");
            }
        }

        if (!Objects.equals(nueva.getHorasElegidas(), actual.getHorasElegidas())) {
            validarModificacionHoras(concurso, actual, estadoActual, nueva.getHorasElegidas());
        }
    }

    /** §3.3: solo con el concurso abierto, en postulaciones pendientes, y sin superar las horas disponibles. */
    private void validarModificacionHoras(Concurso concurso, ConcursoDocente actual,
                                          EstadoPostulacion estadoActual, Integer horasNuevas)
            throws ErrorServiceException {
        validarAbierto(concurso, "no se pueden modificar horas");
        if (ReglasConcurso.esDePreceptor(concurso)) {
            throw new ErrorServiceException("En un concurso de preceptor no se modifican horas: "
                    + "el preceptor toma todas las horas del concurso.");
        }
        if (estadoActual != EstadoPostulacion.PENDIENTE_CERTIFICADOS
                && estadoActual != EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
            throw new ErrorServiceException("Solo se pueden modificar horas de postulaciones pendientes "
                    + "de certificados o de posesión.");
        }
        if (horasNuevas == null || horasNuevas < 1) {
            throw new ErrorServiceException("Indique una cantidad de horas válida (mínimo 1).");
        }
        Integer disponibles = ReglasConcurso.horasDisponibles(concurso, postulacionesDe(concurso.getId()));
        if (disponibles != null) {
            int maximo = (actual.getHorasElegidas() == null ? 0 : actual.getHorasElegidas()) + disponibles;
            if (horasNuevas > maximo) {
                throw new ErrorServiceException("El máximo permitido es " + maximo
                        + " h (horas actuales del docente + horas disponibles del concurso).");
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Concurso concursoDe(ConcursoDocente postulacion) throws ErrorServiceException {
        Long concursoId = postulacion.getConcurso() == null ? null : postulacion.getConcurso().getId();
        if (concursoId == null) {
            throw new ErrorServiceException("Debe indicar el concurso.");
        }
        return concursoRepository.findById(concursoId)
                .filter(concurso -> !Boolean.TRUE.equals(concurso.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("No se encontró el concurso."));
    }

    private void validarAbierto(Concurso concurso, String accion) throws ErrorServiceException {
        if (concurso.getEstadoConcurso() == EstadoConcurso.FINALIZADO) {
            throw new ErrorServiceException("El concurso está cerrado: " + accion + ".");
        }
    }

    private List<ConcursoDocente> postulacionesDe(Long concursoId) {
        return concursoDocenteRepository.findByConcursoIdOrderByIdAsc(concursoId).stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .toList();
    }

    /** Regla general (§6.1): designación ACTIVA del docente en el mismo cargo del concurso. */
    private boolean tieneDesignacionActivaEnElCargo(Concurso concurso, Long docenteId) {
        return designacionesActivasDelCargo(concurso.getCargo()).stream()
                .anyMatch(designacion -> esDelDocente(designacion.getDocente(), docenteId));
    }

    /**
     * Regla de rellamados (§6.2): designación ACTIVA del docente en el cargo, vinculada al concurso origen
     * por concursoId o, en las históricas, por el texto "Concurso: &lt;descripción&gt;".
     * Fail-open si no se puede resolver el origen (§9).
     */
    private boolean tieneDesignacionEfectivaDelOrigen(Concurso concurso, Long docenteId) {
        if (!Boolean.TRUE.equals(concurso.getEsRellamado()) || concurso.getConcursoOrigenId() == null) {
            return false;
        }
        Optional<Concurso> origen = concursoRepository.findById(concurso.getConcursoOrigenId());
        if (origen.isEmpty()) {
            return false;
        }
        String textoOrigen = origen.get().getDescripcion() != null
                ? "Concurso: " + origen.get().getDescripcion() : null;
        return designacionesActivasDelCargo(concurso.getCargo()).stream()
                .filter(designacion -> designacion.getConcursoId() != null
                        ? Objects.equals(designacion.getConcursoId(), origen.get().getId())
                        : textoOrigen != null && textoOrigen.equals(designacion.getOrigenVacancia()))
                .anyMatch(designacion -> esDelDocente(designacion.getDocente(), docenteId));
    }

    private List<Designacion> designacionesActivasDelCargo(Cargo cargo) {
        if (cargo == null || cargo.getDesignaciones() == null) {
            return List.of();
        }
        return cargo.getDesignaciones().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
                .toList();
    }

    private boolean esDelDocente(Docente docente, Long docenteId) {
        return docente != null && Objects.equals(docente.getId(), docenteId);
    }
}
