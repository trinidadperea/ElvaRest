package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

import java.util.List;

@Service
public class ConcursoService extends BaseService<Concurso, Long> {

    private final DesignacionRepository designacionRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;
    private final CargoRepository cargoRepository;

    public ConcursoService(ConcursoRepository repository,
                           DesignacionRepository designacionRepository,
                           ConcursoDocenteRepository concursoDocenteRepository,
                           CargoRepository cargoRepository) {
        super(repository);
        this.designacionRepository = designacionRepository;
        this.concursoDocenteRepository = concursoDocenteRepository;
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
     * Reglas de eliminación de concursos:
     * - EN_PROCESO: no se elimina si tiene designaciones pendientes de certificados o de toma de
     *   posesión (postulaciones vigentes sin designación definitiva —incluidas las no canceladas—
     *   o designaciones en estado PENDIENTE). Los registros sin estado se tratan de forma conservadora.
     * - FINALIZADO: no se elimina si tiene designaciones efectivas que continúan activas (estado ACTIVO).
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Concurso> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        Concurso concurso = encontrado.get();
        if (concurso.getEstadoConcurso() == EstadoConcurso.EN_PROCESO) {
            if (tienePendientes(concurso)) {
                throw new ErrorServiceException("No se puede eliminar el concurso porque tiene designaciones "
                        + "pendientes de certificados o de toma de posesión.");
            }
        } else if (concurso.getEstadoConcurso() == EstadoConcurso.FINALIZADO) {
            if (tieneDesignacionesEfectivasActivas(concurso)) {
                throw new ErrorServiceException("No se puede eliminar el concurso porque tiene designaciones "
                        + "efectivas que continúan activas.");
            }
        }
    }

    private boolean tienePendientes(Concurso concurso) {
        boolean postulacionVigente = concursoDocenteRepository.findAll().stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .filter(postulacion -> postulacion.getConcurso() != null
                        && Objects.equals(postulacion.getConcurso().getId(), concurso.getId()))
                .anyMatch(this::esPendiente);
        if (postulacionVigente) {
            return true;
        }
        return designacionRepository.findAll().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.PENDIENTE)
                .anyMatch(designacion -> vinculadaAlConcurso(designacion, concurso));
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

    private boolean tieneDesignacionesEfectivasActivas(Concurso concurso) {
        return designacionRepository.findAll().stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .filter(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO)
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