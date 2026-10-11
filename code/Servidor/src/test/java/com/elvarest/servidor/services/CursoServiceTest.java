package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Curso;
import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.CursoRepository;
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
 * Reglas de eliminación de cursos:
 * - Regla 6: no se elimina un curso asociado a una materia.
 * - Regla 7: no se elimina un curso asociado a un cargo (a través de la materia del cargo).
 */
@ExtendWith(MockitoExtension.class)
class CursoServiceTest {

    private static final String MENSAJE_REGLA_6 =
            "No se puede eliminar el curso porque está asociado a una materia.";
    private static final String MENSAJE_REGLA_7 =
            "No se puede eliminar el curso porque está asociado a un cargo.";

    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private MateriaRepository materiaRepository;
    @Mock
    private CargoRepository cargoRepository;

    private CursoService service;
    private Curso curso;

    @BeforeEach
    void setUp() {
        service = new CursoService(cursoRepository, materiaRepository, cargoRepository);
        curso = new Curso();
        curso.setId(1L);
    }

    private Materia materiaDe(Curso cursoAsociado, boolean eliminada) {
        Materia materia = new Materia();
        materia.setId(30L);
        materia.setCurso(cursoAsociado);
        materia.setEliminado(eliminada);
        return materia;
    }

    private Cargo cargoDe(Materia materia, boolean eliminado) {
        Cargo cargo = new Cargo();
        cargo.setId(40L);
        cargo.setMateria(materia);
        cargo.setEliminado(eliminado);
        return cargo;
    }

    @Test
    void cursoConMateriaAsociadaRechazaEliminacion() {
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));
        when(materiaRepository.findAll()).thenReturn(List.of(materiaDe(curso, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_6, ex.getMessage());
    }

    @Test
    void cursoAsociadoACargoRechazaEliminacion() {
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(materiaDe(curso, false), false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_7, ex.getMessage());
    }

    @Test
    void materiaYCargoEliminadosNoBloqueanEliminacion() {
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));
        when(materiaRepository.findAll()).thenReturn(List.of(materiaDe(curso, true)));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(materiaDe(curso, false), true)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void materiaDeOtroCursoNoBloqueaEliminacion() {
        Curso otro = new Curso();
        otro.setId(99L);
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));
        when(materiaRepository.findAll()).thenReturn(List.of(materiaDe(otro, false)));
        when(cargoRepository.findAll()).thenReturn(List.of(cargoDe(materiaDe(otro, false), false)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cursoSinRelacionesPermiteEliminacion() {
        when(cursoRepository.findById(1L)).thenReturn(Optional.of(curso));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cursoInexistentePermiteEliminacion() {
        when(cursoRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }
}
