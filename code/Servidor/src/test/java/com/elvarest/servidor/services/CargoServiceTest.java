package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Reglas de eliminación de cargos: no se elimina un cargo con concursos asociados ni con
 * designaciones en cualquier estado (activa, pendiente o finalizada).
 */
@ExtendWith(MockitoExtension.class)
class CargoServiceTest {

    private static final String MENSAJE_CONCURSOS =
            "No se puede eliminar el cargo porque tiene concursos asociados.";
    private static final String MENSAJE_DESIGNACIONES =
            "No se puede eliminar el cargo porque tiene designaciones asociadas.";

    @Mock
    private CargoRepository cargoRepository;
    @Mock
    private ConcursoRepository concursoRepository;

    private CargoService service;
    private Cargo cargo;

    @BeforeEach
    void setUp() {
        service = new CargoService(cargoRepository, concursoRepository);
        cargo = new Cargo();
        cargo.setId(1L);
    }

    private Designacion designacion(EstadoDesignacion estado, boolean eliminada) {
        Designacion designacion = new Designacion();
        designacion.setId(10L);
        designacion.setEstadoDesignacion(estado);
        designacion.setEliminado(eliminada);
        return designacion;
    }

    private Concurso concurso(Long cargoId, boolean eliminado) {
        Concurso concurso = new Concurso();
        concurso.setId(30L);
        Cargo asociado = new Cargo();
        asociado.setId(cargoId);
        concurso.setCargo(asociado);
        concurso.setEliminado(eliminado);
        return concurso;
    }

    @Test
    void cargoConConcursoRechazaEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(1L, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_CONCURSOS, ex.getMessage());
    }

    @Test
    void cargoConConcursoDeOtroCargoPermiteEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(99L, false)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void concursoEliminadoNoBloqueaLaEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(1L, true)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoConDesignacionActivaRechazaEliminacion() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.ACTIVO, false)));
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of());

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void cargoConDesignacionesFinalizadasOPendientesRechazaEliminacion() {
        cargo.setDesignaciones(List.of(
                designacion(EstadoDesignacion.FINALIZADO, false),
                designacion(EstadoDesignacion.PENDIENTE, false)));
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of());

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void designacionEliminadaNoBloqueaEliminacion() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.ACTIVO, true)));
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoSinConcursosNiDesignacionesPermiteEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));
        when(concursoRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoInexistentePermiteEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoDePreceptorSeGuardaSinMateria() {
        cargo.setTipoCargo(TipoCargo.PRECEPTOR);
        cargo.setCargaHoraria(30);
        cargo.setMateria(new Materia());

        service.preAlta(cargo);
        assertNull(cargo.getMateria());
        assertEquals(30, cargo.getCargaHoraria());

        cargo.setMateria(new Materia());
        service.preModificacion(cargo);
        assertNull(cargo.getMateria());
    }

    @Test
    void cargoDocenteConservaLaMateria() {
        cargo.setMateria(new Materia());

        service.preAlta(cargo);

        assertNotNull(cargo.getMateria());
    }
}
