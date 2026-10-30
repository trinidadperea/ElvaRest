package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import com.elvarest.servidor.repositories.DocenteRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class DocenteService extends BaseService<Docente, Long> {

    private final DesignacionRepository designacionRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;

    public DocenteService(DocenteRepository repository,
                          DesignacionRepository designacionRepository,
                          ConcursoDocenteRepository concursoDocenteRepository) {
        super(repository);
        this.designacionRepository = designacionRepository;
        this.concursoDocenteRepository = concursoDocenteRepository;
    }

    /**
     * Reglas de eliminación de docentes:
     * - No se elimina si tiene cualquier designación asociada, sin importar su estado (activa,
     *   pendiente o finalizada): el historial de designaciones queda vinculado al docente.
     * - No se elimina con postulaciones vigentes sin designación definitiva (no canceladas),
     *   contempladas en cualquier concurso. Los registros sin estado de postulación se tratan de
     *   forma conservadora.
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Docente> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }

        boolean tieneDesignaciones = designacionRepository.findAll().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .anyMatch(designacion -> designacion.getDocente() != null
                        && Objects.equals(designacion.getDocente().getId(), id));
        if (tieneDesignaciones) {
            throw new ErrorServiceException("No se puede eliminar el docente porque tiene designaciones asociadas.");
        }

        boolean tienePostulacionesVigentes = concursoDocenteRepository.findAll().stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .filter(postulacion -> postulacion.getDocente() != null
                        && Objects.equals(postulacion.getDocente().getId(), id))
                .anyMatch(this::esPendiente);
        if (tienePostulacionesVigentes) {
            throw new ErrorServiceException("No se puede eliminar el docente porque tiene designaciones "
                    + "pendientes de certificados o de toma de posesión.");
        }
    }

    /**
     * Una postulación cuenta como pendiente si no obtuvo designación definitiva y no fue cancelada.
     * Los registros sin estado de postulación (anteriores a la persistencia del campo) se tratan de
     * forma conservadora, como pendientes.
     */
    private boolean esPendiente(ConcursoDocente postulacion) {
        if (postulacion.isGanador()) {
            return false;
        }
        return postulacion.getEstadoPostulacion() != EstadoPostulacion.CANCELADO_INEFECTIVO;
    }

    @Override
    protected void validar(Docente entidad) throws ErrorServiceException {
        /*if (entidad.getPuntaje() < 0) {
            throw new ErrorServiceException("El puntaje no puede ser negativo");
        }*/
        if (entidad.getArea() == null || entidad.getArea().isBlank()) {
            throw new ErrorServiceException("El área es obligatoria");
        }
    }

    @Override
    protected void preAlta(Docente entidad) throws ErrorServiceException {
        entidad.setActivo(true);
    }
}
