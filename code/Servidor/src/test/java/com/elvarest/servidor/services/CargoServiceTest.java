package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
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
 * Regla de eliminación de cargos (regla 5): no se elimina un cargo con designaciones activas asociadas.
 */
@ExtendWith(MockitoExtension.class)
class CargoServiceTest {

    private static final String MENSAJE_REGLA_5 =
            "No se puede eliminar el cargo porque tiene designaciones activas asociadas.";

    @Mock
    private CargoRepository cargoRepository;

    private CargoService service;
    private Cargo cargo;

    @BeforeEach
    void setUp() {
        service = new CargoService(cargoRepository);
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

    @Test
    void cargoConDesignacionActivaRechazaEliminacion() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.ACTIVO, false)));
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_5, ex.getMessage());
    }

    @Test
    void cargoConDesignacionesNoActivasPermiteEliminacion() {
        cargo.setDesignaciones(List.of(
                designacion(EstadoDesignacion.FINALIZADO, false),
                designacion(EstadoDesignacion.PENDIENTE, false)));
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void designacionActivaEliminadaNoBloqueaEliminacion() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.ACTIVO, true)));
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoSinDesignacionesPermiteEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void cargoInexistentePermiteEliminacion() {
        when(cargoRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }
}
