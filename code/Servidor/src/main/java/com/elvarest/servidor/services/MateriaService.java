package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.MateriaRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class MateriaService extends BaseService<Materia, Long> {

    private final CargoRepository cargoRepository;

    public MateriaService(MateriaRepository repository, CargoRepository cargoRepository) {
        super(repository);
        this.cargoRepository = cargoRepository;
    }

    /** Regla de eliminación de materias: no se elimina una materia asociada a un cargo. */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Materia> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        boolean tieneCargo = cargoRepository.findAll().stream()
                .filter(cargo -> !Boolean.TRUE.equals(cargo.getEliminado()))
                .filter(cargo -> cargo.getMateria() != null)
                .anyMatch(cargo -> Objects.equals(cargo.getMateria().getId(), id));
        if (tieneCargo) {
            throw new ErrorServiceException("No se puede eliminar la materia porque está asociada a un cargo.");
        }
    }
}
