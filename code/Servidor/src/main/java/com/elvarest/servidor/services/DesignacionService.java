package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DesignacionService extends BaseService<Designacion, Long> {

    private final ConcursoRepository concursoRepository;

    public DesignacionService(DesignacionRepository repository, ConcursoRepository concursoRepository) {
        super(repository);
        this.concursoRepository = concursoRepository;
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
}
