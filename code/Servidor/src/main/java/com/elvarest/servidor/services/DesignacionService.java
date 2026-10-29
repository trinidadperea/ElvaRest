package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * La fecha de fin definitiva se puede corregir desde la edición, así que al guardar se
     * reevalúan las alertas: crea la que falte, marca leídas las que ya no corresponden y
     * reabre la que cambió de mensaje.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<Designacion> modificar(Long id, Designacion entidad) throws ErrorServiceException {
        Optional<Designacion> resultado = super.modificar(id, entidad);
        resultado.ifPresent(alertaService::evaluarVencimientoDesignacion);
        return resultado;
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
