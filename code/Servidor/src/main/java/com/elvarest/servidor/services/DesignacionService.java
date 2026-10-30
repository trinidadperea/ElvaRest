package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Service
public class DesignacionService extends BaseService<Designacion, Long> {

    private final ConcursoRepository concursoRepository;
    private final AlertaService alertaService;

    public DesignacionService(DesignacionRepository repository, ConcursoRepository concursoRepository,
                              AlertaService alertaService) {
        super(repository);
        this.concursoRepository = concursoRepository;
        this.alertaService = alertaService;
    }

    /**
     * El estado se deriva de la fecha de fin definitiva en toda alta y edición: sin fecha o con una
     * fecha futura queda ACTIVO; con la fecha de fin alcanzada (hoy o anterior) queda FINALIZADO.
     * PENDIENTE se conserva, porque la toma de posesión la maneja el flujo del concurso y no depende
     * de las fechas. Además se rechaza una fecha de fin anterior a la de inicio.
     */
    @Override
    protected void validar(Designacion entidad) throws ErrorServiceException {
        if (entidad.getFechaFinDefinitiva() != null && entidad.getFechaInicioEfectiva() != null
                && entidad.getFechaFinDefinitiva().isBefore(entidad.getFechaInicioEfectiva())) {
            throw new ErrorServiceException(
                    "La fecha de fin definitiva no puede ser anterior a la fecha de inicio efectiva.");
        }
        entidad.setEstadoDesignacion(
                estadoSegunFechaFin(estadoSolicitado(entidad), entidad.getFechaFinDefinitiva()));
    }

    /**
     * Estado con el que se guarda: el que envía el cliente o, si no envía (el formulario ya no elige
     * el estado), el que tenía guardada la designación. Sin estado previo, todo se deriva de la fecha.
     */
    private EstadoDesignacion estadoSolicitado(Designacion entidad) {
        if (entidad.getEstadoDesignacion() != null || entidad.getId() == null) {
            return entidad.getEstadoDesignacion();
        }
        return repository.findById(entidad.getId())
                .map(Designacion::getEstadoDesignacion)
                .orElse(null);
    }

    /** Sin fecha de fin o con fecha futura -> ACTIVO; con la fecha de fin alcanzada -> FINALIZADO. */
    private EstadoDesignacion estadoSegunFechaFin(EstadoDesignacion solicitado, LocalDate fechaFin) {
        if (solicitado == EstadoDesignacion.PENDIENTE) {
            return EstadoDesignacion.PENDIENTE;
        }
        return fechaFin != null && !fechaFin.isAfter(LocalDate.now())
                ? EstadoDesignacion.FINALIZADO
                : EstadoDesignacion.ACTIVO;
    }

    /**
     * Regla §4: un docente no puede tener dos designaciones vigentes (ACTIVO o PENDIENTE) en el mismo cargo.
     * Aplica a toda designación creada desde un concurso (toma de posesión y "agregar cargo"); el cargo se
     * resuelve por el concurso. Las altas manuales no tienen cargo vinculado (§4) y no se validan.
     */
    @Override
    protected void preAlta(Designacion designacion) throws ErrorServiceException {
        if (designacion.getConcursoId() == null || designacion.getDocente() == null
                || designacion.getDocente().getId() == null) {
            return;
        }
        Cargo cargo = concursoRepository.findById(designacion.getConcursoId())
                .map(concurso -> concurso.getCargo())
                .orElse(null);
        if (cargo == null || cargo.getDesignaciones() == null) {
            return;
        }
        boolean vigente = cargo.getDesignaciones().stream()
                .filter(existente -> !Boolean.TRUE.equals(existente.getEliminado()))
                .filter(existente -> existente.getEstadoDesignacion() == EstadoDesignacion.ACTIVO
                        || existente.getEstadoDesignacion() == EstadoDesignacion.PENDIENTE)
                .anyMatch(existente -> existente.getDocente() != null
                        && Objects.equals(existente.getDocente().getId(), designacion.getDocente().getId()));
        if (vigente) {
            throw new ErrorServiceException("El docente ya posee una designación vigente para este cargo.");
        }
    }

    /** Apenas se crea la designación se evalúa su fecha de fin para generar la alerta si corresponde. */
    @Override
    protected void postAlta(Designacion guardado) throws ErrorServiceException {
        alertaService.evaluarVencimientoDesignacion(guardado);
    }

    /**
     * La fecha de fin definitiva se puede corregir desde la edición: al guardar se deriva el estado
     * (ver {@link #validar}) y se reevalúan las alertas: crea la que falte, marca leídas las que ya
     * no corresponden y reabre la que cambió de mensaje.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<Designacion> modificar(Long id, Designacion entidad) throws ErrorServiceException {
        Optional<Designacion> resultado = super.modificar(id, entidad);
        resultado.ifPresent(alertaService::evaluarVencimientoDesignacion);
        return resultado;
    }

    /**
     * Finaliza las designaciones ACTIVO cuya fecha de fin definitiva ya se alcanzó, sin esperar a que
     * alguien las edite. La revisión diaria la ejecuta antes de depurar las alertas, así el fin de una
     * designación vencida no queda como alerta pendiente.
     *
     * @return cantidad de designaciones finalizadas en esta corrida
     */
    @Transactional(rollbackFor = Exception.class)
    public int finalizarVencidas() {
        int finalizadas = 0;
        for (Designacion designacion : repository.findAll()) {
            if (Boolean.TRUE.equals(designacion.getEliminado())
                    || designacion.getEstadoDesignacion() != EstadoDesignacion.ACTIVO
                    || designacion.getFechaFinDefinitiva() == null
                    || designacion.getFechaFinDefinitiva().isAfter(LocalDate.now())) {
                continue;
            }
            designacion.setEstadoDesignacion(EstadoDesignacion.FINALIZADO);
            repository.save(designacion);
            finalizadas++;
        }
        return finalizadas;
    }

    /**
     * Reglas de eliminación de designaciones: solo se eliminan las PENDIENTE residuales. Las ACTIVO
     * se finalizan cargándoles la fecha de fin definitiva, y las FINALIZADO (y las históricas sin
     * estado) son historial: borrarlas perdería el registro de la designación.
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Designacion> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        EstadoDesignacion estado = encontrado.get().getEstadoDesignacion();
        if (estado == EstadoDesignacion.ACTIVO) {
            throw new ErrorServiceException("No se puede eliminar una designación activa: "
                    + "cárguele la fecha de fin definitiva para finalizarla.");
        }
        if (estado == null) {
            throw new ErrorServiceException("No se puede eliminar una designación histórica: "
                    + "forma parte del historial.");
        }
        if (estado != EstadoDesignacion.PENDIENTE) {
            throw new ErrorServiceException("No se puede eliminar una designación finalizada: "
                    + "forma parte del historial.");
        }
    }

    /** La regla se aplica en el servicio: cualquier vía de baja queda validada, no solo el DELETE. */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        validarEliminacion(id);
    }

    /**
     * Al dar de baja la designación, su alerta de fin ya no tiene causa: queda leída en el acto
     * (la depuración diaria la resolvería recién en la próxima corrida).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bajaLogica(Long id) throws ErrorServiceException {
        boolean dadoDeBaja = super.bajaLogica(id);
        if (dadoDeBaja) {
            repository.findById(id).ifPresent(alertaService::evaluarVencimientoDesignacion);
        }
        return dadoDeBaja;
    }
}
