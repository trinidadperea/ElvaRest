package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DocenteRepository;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final CertificadoRepository certificadoRepository;
    private final TipoCertificadoRepository tipoCertificadoRepository;
    private final DesignacionService designacionService;
    private final ConcursoService concursoService;
    private final CargoRepository cargoRepository;

    public ConcursoDocenteService(
            ConcursoDocenteRepository concursoDocenteRepository,
            ConcursoRepository concursoRepository,
            DocenteRepository docenteRepository,
            CertificadoRepository certificadoRepository,
            TipoCertificadoRepository tipoCertificadoRepository,
            DesignacionService designacionService,
            ConcursoService concursoService,
            CargoRepository cargoRepository) {

        super(concursoDocenteRepository);
        this.concursoDocenteRepository = concursoDocenteRepository;
        this.concursoRepository = concursoRepository;
        this.docenteRepository = docenteRepository;
        this.certificadoRepository = certificadoRepository;
        this.tipoCertificadoRepository = tipoCertificadoRepository;
        this.designacionService = designacionService;
        this.concursoService = concursoService;
        this.cargoRepository = cargoRepository;
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
            // al aprobar el paso 2 deben estar cargados los certificados obligatorios
            if (estadoActual == EstadoPostulacion.PENDIENTE_CERTIFICADOS
                    && nueva.getEstadoPostulacion() == EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
                validarCertificadosObligatorios(actual);
            }
        }

        if (!Objects.equals(nueva.getHorasElegidas(), actual.getHorasElegidas())) {
            validarModificacionHoras(concurso, actual, estadoActual, nueva.getHorasElegidas());
        }
    }

    /**
     * Las postulaciones no se eliminan: se cancelan declarándolas inefectivas, que conserva el
     * historial de la toma de horas y libera las horas del concurso.
     */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        Optional<ConcursoDocente> guardada = concursoDocenteRepository.findById(id);
        if (guardada.isEmpty() || Boolean.TRUE.equals(guardada.get().getEliminado())) {
            return; // BaseService responde "no encontrada".
        }
        throw new ErrorServiceException("Las postulaciones no se eliminan: se cancelan declarándolas inefectivas.");
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

    /** Paso 2: para aprobar, la postulación debe tener cargado un certificado actual de cada tipo obligatorio */
    private void validarCertificadosObligatorios(ConcursoDocente postulacion) throws ErrorServiceException {
        Set<Long> cargados = certificadoRepository
                .findByConcursoDocenteIdAndActualTrueAndEliminadoFalse(postulacion.getId()).stream()
                .filter(certificado -> certificado.getTipoCertificado() != null)
                .map(certificado -> certificado.getTipoCertificado().getId())
                .collect(Collectors.toSet());
        String faltantes = tipoCertificadoRepository.findAll().stream()
                .filter(tipo -> !Boolean.TRUE.equals(tipo.getEliminado()) && tipo.isObligatorio())
                .filter(tipo -> !cargados.contains(tipo.getId()))
                .map(TipoCertificado::getNombre)
                .collect(Collectors.joining(", "));
        if (!faltantes.isEmpty()) {
            throw new ErrorServiceException("No se puede aprobar: falta cargar el certificado obligatorio: " + faltantes + ".");
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

    // ------------------------------------------------------------------
    // §3.7 Paso 3: confirmación de toma de posesión
    // ------------------------------------------------------------------

    /**
     * Confirma la toma de posesión como una sola operación: crea la designación (con las reglas de
     * §4 y de fechas de {@link DesignacionService}), la vincula al cargo, avanza la postulación a
     * designación definitiva y cierra el concurso si quedó completo. Al vivir en el servicio,
     * cualquier llamada a la API obtiene el mismo resultado y no puede quedar a mitad de camino.
     */
    @Transactional(rollbackFor = Exception.class)
    public Designacion confirmarPosesion(Long concursoId, Long postulacionId, Designacion datos)
            throws ErrorServiceException {
        if (datos == null) {
            throw new ErrorServiceException("Complete los datos de la designación.");
        }
        ConcursoDocente postulacion = concursoDocenteRepository.findById(postulacionId)
                .filter(encontrada -> !Boolean.TRUE.equals(encontrada.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("No se encontró la postulación."));
        Concurso concurso = concursoRepository.findById(concursoId)
                .filter(encontrado -> !Boolean.TRUE.equals(encontrado.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("No se encontró el concurso."));
        if (postulacion.getConcurso() == null || !Objects.equals(postulacion.getConcurso().getId(), concursoId)) {
            throw new ErrorServiceException("La postulación no pertenece a este concurso.");
        }
        validarAbierto(concurso, "no se puede confirmar la toma de posesión");
        if (ReglasConcurso.estadoEfectivo(postulacion) != EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION) {
            throw new ErrorServiceException("La postulación no está pendiente de toma de posesión.");
        }
        if (concurso.getCaracterDesignacion() == null) {
            throw new ErrorServiceException("El concurso no tiene un carácter de designación definido. "
                    + "Complete el carácter del concurso antes de confirmar la toma de posesión.");
        }
        if (datos.getFechaInicioEfectiva() == null || datos.getFechaFinDefinitiva() == null) {
            throw new ErrorServiceException("Complete las fechas de inicio efectiva y de fin definitiva.");
        }
        completarDesdeElConcurso(datos, concurso, postulacion);

        Designacion designacion = designacionService.alta(datos);
        vincularAlCargo(concurso.getCargo(), designacion);

        postulacion.setGanador(true);
        postulacion.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);
        concursoDocenteRepository.save(postulacion);

        if (ReglasConcurso.esListoParaCierre(concurso, postulacionesDe(concursoId))) {
            concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
            concursoService.modificar(concurso.getId(), concurso);
        }
        return designacion;
    }

    /** La designación hereda del concurso lo que no se carga en el formulario de posesión. */
    private void completarDesdeElConcurso(Designacion designacion, Concurso concurso, ConcursoDocente postulacion) {
        designacion.setId(null);
        designacion.setDocente(postulacion.getDocente());
        designacion.setCaracterDesignacion(concurso.getCaracterDesignacion());
        designacion.setEstadoDesignacion(EstadoDesignacion.ACTIVO);
        designacion.setOrigenVacancia(origenVacanciaDe(concurso));
        designacion.setMotivoVacancia(concurso.getMotivoVacancia());
        designacion.setProfesorReemplazante(concurso.getProfesorReemplazante());
        designacion.setConcursoId(concurso.getId());
        designacion.setAlertas(new ArrayList<>());
        designacion.setLicencias(new ArrayList<>());
    }

    /**
     * Origen de vacancia heredado; los concursos anteriores al campo guardan el respaldo
     * "Concurso: &lt;descripción&gt;", que es el mismo criterio del resto del flujo.
     */
    private String origenVacanciaDe(Concurso concurso) {
        return concurso.getOrigenVacancia() != null && !concurso.getOrigenVacancia().isBlank()
                ? concurso.getOrigenVacancia()
                : "Concurso: " + ReglasConcurso.descripcion(concurso);
    }

    /** El vínculo designación -> cargo es unidireccional y sin cascade: se guarda la colección del cargo. */
    private void vincularAlCargo(Cargo cargo, Designacion designacion) {
        if (cargo == null || cargo.getId() == null) {
            return;
        }
        Cargo guardado = cargoRepository.findById(cargo.getId()).orElse(null);
        if (guardado == null) {
            return;
        }
        if (guardado.getDesignaciones() == null) {
            guardado.setDesignaciones(new ArrayList<>());
        }
        guardado.getDesignaciones().add(designacion);
        cargoRepository.save(guardado);
    }

    private boolean esDelDocente(Docente docente, Long docenteId) {
        return docente != null && Objects.equals(docente.getId(), docenteId);
    }
}