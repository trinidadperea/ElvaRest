package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.MateriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Regla de eliminación de materias (regla 7): no se elimina una materia asociada a un cargo.
 */
@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

    private static final String MENSAJE_REGLA_7 =
            "No se puede eliminar la materia porque está asociada a un cargo.";

    @Mock
    private MateriaRepository materiaRepository;
    @Mock
    private CargoRepository cargoRepository;

    private MateriaService service;
    private Materia materia;

    @BeforeEach
    void setUp() {
        service = new MateriaService(materiaRepository, cargoRepository);
        materia = new Materia();
        materia.setId(1L);
    }

    private Cargo cargoDe(Materia materiaAsociada, boolean eliminado) {
        Cargo cargo = new Cargo();
        cargo.setId(40L);
        cargo.setMateria(materiaAsociada);
        cargo.setEliminado(eliminado);
        return cargo;
    }

    @Test
    void materiaAsociadaACargoRechazaEliminacion() {
        when(materiaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(materia, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_7, ex.getMessage());
    }

    @Test
    void cargoEliminadoNoBloqueaEliminacion() {
        when(materiaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(materia, true)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoDeOtraMateriaNoBloqueaEliminacion() {
        Materia otra = new Materia();
        otra.setId(99L);
        when(materiaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(otra, false)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void materiaSinCargosPermiteEliminacion() {
        when(materiaRepository.findById(1L)).thenReturn(Optional.of(materia));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void materiaInexistentePermiteEliminacion() {
        when(materiaRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void bajaLogicaAplicaLaReglaEnElServicio() {
        // La regla tiene que estar enganchada en preBaja: si no, el DELETE la saltea.
        when(materiaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(materia, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.bajaLogica(1L));
        assertEquals(MENSAJE_REGLA_7, ex.getMessage());
    }
}
