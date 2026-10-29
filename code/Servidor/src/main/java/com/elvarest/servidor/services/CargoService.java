package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CargoService extends BaseService<Cargo, Long> {

    public CargoService(CargoRepository repository) {
        super(repository);
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
     * Regla de eliminación de cargos: no se elimina un cargo con designaciones activas asociadas.
     * Se contemplan todas las designaciones vinculadas al cargo (colección del modelo) con el
     * mismo criterio de actividad que usa el sistema: no eliminadas y en estado ACTIVO.
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Cargo> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        List<Designacion> designaciones = encontrado.get().getDesignaciones();
        if (designaciones == null) {
            return;
        }
        boolean tieneActivas = designaciones.stream()
                .filter(designacion -> !Boolean.TRUE.equals(designacion.getEliminado()))
                .anyMatch(designacion -> designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO);
        if (tieneActivas) {
            throw new ErrorServiceException("No se puede eliminar el cargo porque tiene designaciones activas asociadas.");
        }
    }
}
