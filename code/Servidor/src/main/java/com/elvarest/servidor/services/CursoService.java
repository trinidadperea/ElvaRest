package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Curso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.CursoRepository;
import com.elvarest.servidor.repositories.MateriaRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class CursoService extends BaseService<Curso, Long> {

    private final MateriaRepository materiaRepository;
    private final CargoRepository cargoRepository;

    public CursoService(CursoRepository repository, MateriaRepository materiaRepository,
                        CargoRepository cargoRepository) {
        super(repository);
        this.materiaRepository = materiaRepository;
        this.cargoRepository = cargoRepository;
    }

    /**
     * Reglas de eliminación de cursos:
     * - No se elimina un curso asociado a una materia.
     * - No se elimina un curso asociado a un cargo (a través de la materia del cargo).
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<Curso> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        boolean tieneMateria = materiaRepository.findAll().stream()
                .filter(materia -> !Boolean.TRUE.equals(materia.getEliminado()))
                .filter(materia -> materia.getCurso() != null)
                .anyMatch(materia -> Objects.equals(materia.getCurso().getId(), id));
        if (tieneMateria) {
            throw new ErrorServiceException("No se puede eliminar el curso porque está asociado a una materia.");
        }
        boolean tieneCargo = cargoRepository.findAll().stream()
                .filter(cargo -> !Boolean.TRUE.equals(cargo.getEliminado()))
                .filter(cargo -> cargo.getMateria() != null && cargo.getMateria().getCurso() != null)
                .anyMatch(cargo -> Objects.equals(cargo.getMateria().getCurso().getId(), id));
        if (tieneCargo) {
            throw new ErrorServiceException("No se puede eliminar el curso porque está asociado a un cargo.");
        }
    }
}
