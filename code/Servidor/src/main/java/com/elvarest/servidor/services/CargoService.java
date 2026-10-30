package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CargoService extends BaseService<Cargo, Long> {

    private final ConcursoRepository concursoRepository;

    public CargoService(CargoRepository repository, ConcursoRepository concursoRepository) {
        super(repository);
        this.concursoRepository = concursoRepository;
    }

    @Override
    protected void preAlta(Cargo cargo) {
        sinMateriaSiEsPreceptor(cargo);
    }

    @Override
    protected void preModificacion(Cargo cargo) {
        sinMateriaSiEsPreceptor(cargo);
    }

    /** Un cargo de preceptor no tiene materia, curso ni año: se asigna fuera del sistema. */
    private void sinMateriaSiEsPreceptor(Cargo cargo) {
        if (cargo.getTipoCargo() == TipoCargo.PRECEPTOR) {
            cargo.setMateria(null);
        }
    }

    /**
     * Reglas de eliminación de cargos: no se elimina un cargo con concursos asociados ni con
     * designaciones en cualquier estado (activa, pendiente o finalizada). Los concursos apuntan al
     * cargo y las designaciones conservan su historial: borrarlo dejaría esos registros sin contexto.
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Cargo> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        Long cargoId = encontrado.get().getId();
        boolean tieneConcursos = concursoRepository.findAll().stream()
                .filter(concurso -> !Boolean.TRUE.equals(concurso.getEliminado()))
                .filter(concurso -> concurso.getCargo() != null
                        && Objects.equals(concurso.getCargo().getId(), cargoId))
                .findAny().isPresent();
        if (tieneConcursos) {
            throw new ErrorServiceException("No se puede eliminar el cargo porque tiene concursos asociados.");
        }
        List<Designacion> designaciones = encontrado.get().getDesignaciones();
        boolean tieneDesignaciones = designaciones != null && designaciones.stream()
                .anyMatch(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()));
        if (tieneDesignaciones) {
            throw new ErrorServiceException("No se puede eliminar el cargo porque tiene designaciones asociadas.");
        }
    }

    /** La regla se aplica en el servicio: cualquier vía de baja queda validada, no solo el DELETE. */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        validarEliminacion(id);
    }
}
