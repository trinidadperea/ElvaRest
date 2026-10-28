package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
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
 * Regla: un cargo no puede tener dos concursos abiertos a la vez, entendiendo "abierto" por el
 * estado (PUBLICADO o EN_PROCESO) y no por las fechas.
 * - En el alta se controla siempre (salvo que el nuevo concurso nazca FINALIZADO).
 * - En la modificación solo cuando cambia el cargo, para no bloquear la edición de registros
 *   históricos ni el cierre de un concurso que quedó duplicado con anterioridad.
 */
@ExtendWith(MockitoExtension.class)
class ConcursoServiceCargoAbiertoTest {

    private static final String MENSAJE_CARGO_ABIERTO =
            "El cargo ya tiene un concurso abierto: ciérrelo antes de abrir otro.";

    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;
    @Mock
    private AlertaService alertaService;

    private ConcursoService service;

    @BeforeEach
    void setUp() {
        service = new ConcursoService(concursoRepository, designacionRepository, concursoDocenteRepository,
                alertaService);
    }

    private Concurso concurso(Long id, Long cargoId, EstadoConcurso estado, boolean eliminado) {
        Concurso concurso = new Concurso();
        concurso.setId(id);
        concurso.setEstadoConcurso(estado);
        concurso.setEliminado(eliminado);
        if (cargoId != null) {
            Cargo cargo = new Cargo();
            cargo.setId(cargoId);
            concurso.setCargo(cargo);
        }
        return concurso;
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    void altaConCargoConConcursoEnProcesoRechaza() {
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 1L, EstadoConcurso.EN_PROCESO, false)));
        Concurso nuevo = concurso(null, 1L, EstadoConcurso.PUBLICADO, false);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(nuevo));
        assertEquals(MENSAJE_CARGO_ABIERTO, ex.getMessage());
    }

    @Test
    void altaConCargoConConcursoPublicadoRechaza() {
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 1L, EstadoConcurso.PUBLICADO, false)));
        Concurso nuevo = concurso(null, 1L, EstadoConcurso.PUBLICADO, false);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(nuevo));
        assertEquals(MENSAJE_CARGO_ABIERTO, ex.getMessage());
    }

    @Test
    void altaConCargoDeConcursoFinalizadoPermite() {
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 1L, EstadoConcurso.FINALIZADO, false)));
        Concurso nuevo = concurso(null, 1L, EstadoConcurso.PUBLICADO, false);

        assertDoesNotThrow(() -> service.alta(nuevo));
    }

    @Test
    void altaConCargoDeConcursoEliminadoPermite() {
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 1L, EstadoConcurso.EN_PROCESO, true)));
        Concurso nuevo = concurso(null, 1L, EstadoConcurso.PUBLICADO, false);

        assertDoesNotThrow(() -> service.alta(nuevo));
    }

    @Test
    void altaConCargoLibrePermite() {
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 9L, EstadoConcurso.EN_PROCESO, false)));
        Concurso nuevo = concurso(null, 1L, EstadoConcurso.PUBLICADO, false);

        assertDoesNotThrow(() -> service.alta(nuevo));
    }

    @Test
    void altaSinCargoPermite() {
        Concurso nuevo = concurso(null, null, EstadoConcurso.PUBLICADO, false);

        assertDoesNotThrow(() -> service.alta(nuevo));
    }

    @Test
    void altaQueNaceFinalizadaNoValidaDuplicado() {
        Concurso nuevo = concurso(null, 1L, EstadoConcurso.FINALIZADO, false);

        assertDoesNotThrow(() -> service.alta(nuevo));
    }

    // ------------------------------------------------------------------
    // Modificación
    // ------------------------------------------------------------------

    @Test
    void modificacionConElMismoCargoPermite() {
        when(concursoRepository.findById(1L))
                .thenReturn(Optional.of(concurso(1L, 1L, EstadoConcurso.PUBLICADO, false)));
        Concurso modificado = concurso(1L, 1L, EstadoConcurso.EN_PROCESO, false);

        assertDoesNotThrow(() -> service.modificar(1L, modificado));
    }

    @Test
    void modificacionCambioACargoOcupadoRechaza() {
        when(concursoRepository.findById(1L))
                .thenReturn(Optional.of(concurso(1L, 1L, EstadoConcurso.PUBLICADO, false)));
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 2L, EstadoConcurso.EN_PROCESO, false)));
        Concurso modificado = concurso(1L, 2L, EstadoConcurso.PUBLICADO, false);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.modificar(1L, modificado));
        assertEquals(MENSAJE_CARGO_ABIERTO, ex.getMessage());
    }

    @Test
    void modificacionCambioACargoLibrePermite() {
        when(concursoRepository.findById(1L))
                .thenReturn(Optional.of(concurso(1L, 1L, EstadoConcurso.PUBLICADO, false)));
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 2L, EstadoConcurso.FINALIZADO, false)));
        Concurso modificado = concurso(1L, 2L, EstadoConcurso.PUBLICADO, false);

        assertDoesNotThrow(() -> service.modificar(1L, modificado));
    }

    @Test
    void modificacionQueCierraElConcursoNoValidaDuplicado() {
        when(concursoRepository.findById(1L))
                .thenReturn(Optional.of(concurso(1L, 1L, EstadoConcurso.EN_PROCESO, false)));
        Concurso modificado = concurso(1L, 2L, EstadoConcurso.FINALIZADO, false);

        assertDoesNotThrow(() -> service.modificar(1L, modificado));
    }

    @Test
    void modificacionQueAsignaCargoAOcupadoRechaza() {
        when(concursoRepository.findById(1L))
                .thenReturn(Optional.of(concurso(1L, null, EstadoConcurso.PUBLICADO, false)));
        when(concursoRepository.findAll()).thenReturn(List.of(concurso(2L, 2L, EstadoConcurso.PUBLICADO, false)));
        Concurso modificado = concurso(1L, 2L, EstadoConcurso.PUBLICADO, false);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.modificar(1L, modificado));
        assertEquals(MENSAJE_CARGO_ABIERTO, ex.getMessage());
    }
}
