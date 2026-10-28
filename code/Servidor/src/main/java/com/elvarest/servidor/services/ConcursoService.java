package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

import java.util.List;

@Service
public class ConcursoService extends BaseService<Concurso, Long> {

    private final DesignacionRepository designacionRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;
    private final AlertaService alertaService;

    public ConcursoService(ConcursoRepository repository,
                           DesignacionRepository designacionRepository,
                           ConcursoDocenteRepository concursoDocenteRepository,
                           AlertaService alertaService) {
        super(repository);
        this.designacionRepository = designacionRepository;
        this.concursoDocenteRepository = concursoDocenteRepository;
        this.alertaService = alertaService;
    }

    @Override
    protected void validar(Concurso concurso) throws ErrorServiceException {
        if (concurso.getEstadoConcurso() == null) {
            throw new ErrorServiceException("Debe seleccionar un estado para el concurso.");
        }
    }

    /**
     * Regla: un cargo no puede tener dos concursos abiertos a la vez. "Abierto" se define por el
     * estado (PUBLICADO o EN_PROCESO), no por las fechas. En el alta se controla siempre y en la
     * modificación solo cuando cambia el cargo, para no bloquear la edición de registros
     * históricos ni el cierre de un concurso que quedó duplicado con anterioridad.
     */
    @Override
    protected void preAlta(Concurso concurso) throws ErrorServiceException {
        if (concurso.getEstadoConcurso() != EstadoConcurso.FINALIZADO) {
            validarCargoSinConcursoAbierto(cargoIdDe(concurso), null);
        }
    }

    @Override
    protected void preModificacion(Concurso concurso) throws ErrorServiceException {
        Long cargoAnterior = concurso.getId() == null ? null
                : repository.findById(concurso.getId())
                        .filter(actual -> actual.getCargo() != null)
                        .map(actual -> actual.getCargo().getId())
                        .orElse(null);
        Long cargoNuevo = cargoIdDe(concurso);
        if (concurso.getEstadoConcurso() != EstadoConcurso.FINALIZADO
                && cargoNuevo != null && !Objects.equals(cargoNuevo, cargoAnterior)) {
            validarCargoSinConcursoAbierto(cargoNuevo, concurso.getId());
        }
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