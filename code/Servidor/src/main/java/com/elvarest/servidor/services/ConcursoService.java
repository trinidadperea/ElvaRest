package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import java.util.List;

@Service
public class ConcursoService extends BaseService<Concurso, Long> {

    private final DesignacionRepository designacionRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;
    private final AlertaService alertaService;
    private final CargoRepository cargoRepository;

    public ConcursoService(ConcursoRepository repository,
                           DesignacionRepository designacionRepository,
                           ConcursoDocenteRepository concursoDocenteRepository,
                           AlertaService alertaService,
                           CargoRepository cargoRepository) {
        super(repository);
        this.designacionRepository = designacionRepository;
        this.concursoDocenteRepository = concursoDocenteRepository;
        this.alertaService = alertaService;
        this.cargoRepository = cargoRepository;
    }

    @Override
    protected void validar(Concurso concurso) throws ErrorServiceException {
        if (concurso.getEstadoConcurso() == null) {
            throw new ErrorServiceException("Debe seleccionar un estado para el concurso.");
        }
        validarHorasDelCargo(concurso);
    }

    /** Las horas totales del concurso no pueden superar la carga horaria del cargo (si el cargo la tiene). */
    private void validarHorasDelCargo(Concurso concurso) throws ErrorServiceException {
        Long cargoId = concurso.getCargo() == null ? null : concurso.getCargo().getId();
        if (concurso.getHorasTotales() == null || cargoId == null) {
            return;
        }
        // Se lee el cargo guardado: el formulario solo envía su id.
        Optional<Cargo> cargo = cargoRepository.findById(cargoId);
        if (cargo.isEmpty() || cargo.get().getCargaHoraria() <= 0) {
            return;
        }
        int cargaHoraria = cargo.get().getCargaHoraria();
        if (concurso.getHorasTotales() > cargaHoraria) {
            throw new ErrorServiceException("Las horas totales del concurso (" + concurso.getHorasTotales()
                    + " h) no pueden superar la carga horaria del cargo (" + cargaHoraria + " h).");
        }
    }

    /**
     * Validaciones de alta. El concurso es la única fuente de la configuración: sin descripción,
     * cargo, fecha y horas no hay con qué abrirlo, y sin carácter la designación posterior no se
     * puede emitir. En un rellamado el cargo, el carácter y las horas se heredan del origen.
     *
     * Además se aplica la regla del cargo: un cargo no puede tener dos concursos abiertos a la vez
     * ("abierto" se define por el estado PUBLICADO o EN_PROCESO, no por las fechas). En la
     * modificación se controla solo cuando cambia el cargo, para no bloquear la edición de
     * registros históricos ni el cierre de un concurso que quedó duplicado con anterioridad.
     */
    @Override
    protected void preAlta(Concurso concurso) throws ErrorServiceException {
        boolean rellamado = concurso.getConcursoOrigenId() != null;
        concurso.setEsRellamado(rellamado);
        if (concurso.getDescripcion() == null || concurso.getDescripcion().isBlank()) {
            throw new ErrorServiceException("La descripción del concurso es obligatoria.");
        }
        if (concurso.getCargo() == null) {
            throw new ErrorServiceException("Seleccione el cargo vacante del concurso.");
        }
        if (concurso.getFechaCierre() == null) {
            throw new ErrorServiceException("Indique la fecha y hora del concurso.");
        }
        // El carácter de la designación se hereda en un rellamado: solo se exige al abrir el concurso.
        if (!rellamado && concurso.getCaracterDesignacion() == null) {
            throw new ErrorServiceException("Seleccione la designación (carácter de designación) del concurso.");
        }
        if (concurso.getFechaApertura() == null) {
            concurso.setFechaApertura(LocalDate.now());
        }
        completarHorasTotales(concurso);
        validarCargoSinConcursoAbierto(cargoIdDe(concurso), null);
    }

    /**
     * Las horas totales se persisten con el concurso: si el formulario no las envía, se toma la carga
     * horaria del cargo como respaldo. Antes se derivaban en cada lectura, así que un cambio en la
     * carga horaria del cargo reescribía la historia de los concursos ya creados.
     */
    private void completarHorasTotales(Concurso concurso) throws ErrorServiceException {
        if (concurso.getHorasTotales() != null && concurso.getHorasTotales() > 0) {
            return;
        }
        Long cargoId = cargoIdDe(concurso);
        int cargaHoraria = cargoId == null ? 0
                : cargoRepository.findById(cargoId).map(Cargo::getCargaHoraria).orElse(0);
        if (cargaHoraria <= 0) {
            throw new ErrorServiceException("El concurso no tiene horas totales: indique las horas totales "
                    + "o complete la carga horaria del cargo.");
        }
        concurso.setHorasTotales(cargaHoraria);
    }

    @Override
    protected void preModificacion(Concurso concurso) throws ErrorServiceException {
        Optional<Concurso> guardado = concurso.getId() == null
                ? Optional.<Concurso>empty()
                : repository.findById(concurso.getId());
        Long cargoAnterior = guardado.filter(actual -> actual.getCargo() != null)
                .map(actual -> actual.getCargo().getId())
                .orElse(null);
        Long cargoNuevo = cargoIdDe(concurso);
        if (concurso.getEstadoConcurso() != EstadoConcurso.FINALIZADO
                && cargoNuevo != null && !Objects.equals(cargoNuevo, cargoAnterior)) {
            validarCargoSinConcursoAbierto(cargoNuevo, concurso.getId());
        }
        validarCierreSinPendientes(concurso, guardado);
    }

    /**
     * Alta de concurso. El estado inicial lo fija el servicio: todo concurso nace PUBLICADO y el
     * formulario no lo elige. Si viene de un concurso origen (rellamado), además hereda cargo,
     * carácter, motivo y horas remanentes, y deja la transferencia asentada en el origen dentro de
     * la misma transacción: el Cliente ya no calcula ni escribe nada de esto.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Concurso alta(Concurso concurso) throws ErrorServiceException {
        concurso.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        if (concurso.getConcursoOrigenId() == null) {
            return super.alta(concurso);
        }
        Concurso origen = prepararRellamado(concurso);
        Concurso guardado = super.alta(concurso);
        // Las horas remanentes quedan transferidas: el origen se queda sin horas disponibles.
        origen.setHorasTransferidas(concurso.getHorasTotales());
        repository.save(origen);
        return guardado;
    }

    /**
     * Un rellamado solo puede nacer de un concurso cerrado con horas remanentes. Se ejecuta antes
     * de {@code validar}/{@code preAlta} para que las validaciones vean el cargo heredado.
     */
    private Concurso prepararRellamado(Concurso rellamado) throws ErrorServiceException {
        Concurso origen = repository.findById(rellamado.getConcursoOrigenId())
                .filter(encontrado -> !Boolean.TRUE.equals(encontrado.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("El concurso origen del rellamado no existe."));
        if (origen.getEstadoConcurso() != EstadoConcurso.FINALIZADO) {
            throw new ErrorServiceException("El concurso origen debe estar cerrado para generar un rellamado.");
        }
        Integer remanentes = ReglasConcurso.horasDisponibles(origen, postulacionesDe(origen.getId()));
        if (remanentes == null || remanentes <= 0) {
            throw new ErrorServiceException("El concurso origen no tiene horas remanentes para transferir.");
        }
        rellamado.setCargo(origen.getCargo());
        rellamado.setCaracterDesignacion(origen.getCaracterDesignacion());
        rellamado.setOrigenVacancia(origen.getOrigenVacancia());
        rellamado.setMotivoVacancia(origen.getMotivoVacancia());
        rellamado.setProfesorReemplazante(origen.getProfesorReemplazante());
        rellamado.setEsRellamado(true);
        rellamado.setHorasTotales(remanentes);
        if (rellamado.getDescripcion() == null || rellamado.getDescripcion().isBlank()) {
            rellamado.setDescripcion("Rellamado – " + ReglasConcurso.descripcion(origen));
        }
        if (rellamado.getFechaApertura() == null) {
            rellamado.setFechaApertura(LocalDate.now());
        }
        return origen;
    }

    /**
     * El cierre (manual o automático) y la corrección de datos se guardan con este método: después
     * de persistir se reevalúan las alertas del concurso. Así el cierre marca leída la alerta en el
     * acto y una edición de un concurso vencido la genera sin esperar al scheduler.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<Concurso> modificar(Long id, Concurso entidad) throws ErrorServiceException {
        Optional<Concurso> resultado = super.modificar(id, entidad);
        resultado.ifPresent(alertaService::evaluarConcursoSinCerrar);
        return resultado;
    }

    /**
     * Al dar de baja el concurso, sus dos alertas (sigue en proceso y horas sin cubrir) ya no tienen
     * causa y quedan leídas en el acto; la baja es lógica, así que la alerta se conserva como historial.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bajaLogica(Long id) throws ErrorServiceException {
        boolean dadoDeBaja = super.bajaLogica(id);
        if (dadoDeBaja) {
            repository.findById(id).ifPresent(alertaService::evaluarConcursoSinCerrar);
        }
        return dadoDeBaja;
    }

    private Long cargoIdDe(Concurso concurso) {
        return concurso.getCargo() != null ? concurso.getCargo().getId() : null;
    }

    private void validarCargoSinConcursoAbierto(Long cargoId, Long concursoExcluido) throws ErrorServiceException {
        if (cargoId == null) {
            return;
        }
        boolean otroAbierto = repository.findAll().stream()
                .filter(otro -> !Boolean.TRUE.equals(otro.getEliminado()))
                .filter(otro -> !Objects.equals(otro.getId(), concursoExcluido))
                .filter(otro -> otro.getEstadoConcurso() != EstadoConcurso.FINALIZADO)
                .anyMatch(otro -> otro.getCargo() != null && Objects.equals(otro.getCargo().getId(), cargoId));
        if (otroAbierto) {
            throw new ErrorServiceException("El cargo ya tiene un concurso abierto: ciérrelo antes de abrir otro.");
        }
    }

    /**
     * Regla de cierre: un concurso no se cierra mientras queden docentes pendientes de validar
     * certificados. La validación vive en el servicio, así que también aplica a una llamada directa
     * a la API y al cierre automático del flujo.
     */
    private void validarCierreSinPendientes(Concurso concurso, Optional<Concurso> guardado)
            throws ErrorServiceException {
        if (concurso.getEstadoConcurso() != EstadoConcurso.FINALIZADO) {
            return;
        }
        if (guardado.isEmpty() || guardado.get().getEstadoConcurso() == EstadoConcurso.FINALIZADO) {
            return; // ya estaba cerrado: no es un cierre nuevo
        }
        long pendientes = postulacionesDe(concurso.getId()).stream()
                .filter(postulacion -> ReglasConcurso.estadoEfectivo(postulacion)
                        == EstadoPostulacion.PENDIENTE_CERTIFICADOS)
                .count();
        if (pendientes > 0) {
            throw new ErrorServiceException("No se puede cerrar el concurso: hay " + pendientes
                    + " docente(s) pendientes de validación de certificados.");
        }
    }

    /**
     * Reglas de eliminación de concursos: un concurso con actividad no se elimina.
     * - Postulaciones: bloquea cualquier toma de horas no eliminada ni cancelada. Las declaradas
     *   inefectivas no cuentan, así un concurso sin docentes vigentes se puede limpiar.
     * - Designaciones: bloquea cualquier designación vinculada, sin importar su estado, porque el
     *   historial de la designación apunta al concurso (concursoId).
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Concurso> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        Concurso concurso = encontrado.get();
        if (tienePostulacionesVigentes(concurso)) {
            throw new ErrorServiceException("No se puede eliminar el concurso porque tiene postulaciones asociadas.");
        }
        if (tieneDesignacionesVinculadas(concurso)) {
            throw new ErrorServiceException("No se puede eliminar el concurso porque tiene designaciones asociadas.");
        }
    }

    /** La regla se aplica en el servicio: cualquier vía de baja queda validada, no solo el DELETE. */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        validarEliminacion(id);
    }

    private boolean tienePostulacionesVigentes(Concurso concurso) {
        return postulacionesDe(concurso.getId()).stream()
                .anyMatch(postulacion -> ReglasConcurso.estadoEfectivo(postulacion)
                        != EstadoPostulacion.CANCELADO_INEFECTIVO);
    }

    /** Postulaciones (tomas de horas) no eliminadas del concurso. */
    private List<ConcursoDocente> postulacionesDe(Long concursoId) {
        if (concursoId == null) {
            return List.of();
        }
        return concursoDocenteRepository.findAll().stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .filter(postulacion -> postulacion.getConcurso() != null
                        && Objects.equals(postulacion.getConcurso().getId(), concursoId))
                .toList();
    }

    private boolean tieneDesignacionesVinculadas(Concurso concurso) {
        return designacionRepository.findAll().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .anyMatch(designacion -> vinculadaAlConcurso(designacion, concurso));
    }

    /**
     * Vínculo de la designación con el concurso: por el id directo o, en las históricas,
     * por el texto "Concurso: &lt;descripción&gt;" (mismo criterio que usa el flujo del Cliente).
     */
    private boolean vinculadaAlConcurso(Designacion designacion, Concurso concurso) {
        if (designacion.getConcursoId() != null) {
            return Objects.equals(designacion.getConcursoId(), concurso.getId());
        }
        String esperado = concurso.getDescripcion() != null ? "Concurso: " + concurso.getDescripcion() : null;
        return esperado != null && esperado.equals(designacion.getOrigenVacancia());
    }

    public Docente obtenerPrimero(Long concursoId) {

        List<ConcursoDocente> ordenMerito =
                concursoDocenteRepository
                        .findByConcursoIdOrderByIdAsc(concursoId);

        if (ordenMerito.isEmpty()) {
            return null;
        }

        return ordenMerito.get(0).getDocente();
    }

    public Docente obtenerSiguienteEnOrden(
            Long concursoId,
            Long idDocenteActual) {

        List<ConcursoDocente> ordenMerito =
                concursoDocenteRepository
                        .findByConcursoIdOrderByIdAsc(concursoId);

        for (int i = 0; i < ordenMerito.size(); i++) {

            Docente docenteActual =
                    ordenMerito.get(i).getDocente();

            if (docenteActual.getId().equals(idDocenteActual)) {

                if (i + 1 < ordenMerito.size()) {
                    return ordenMerito
                            .get(i + 1)
                            .getDocente();
                }

                return null;
            }
        }

        return null;
    }
}