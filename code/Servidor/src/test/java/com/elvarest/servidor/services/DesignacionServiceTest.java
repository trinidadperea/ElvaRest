package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;

/**
 * Regla §4: un docente no puede tener dos designaciones vigentes (ACTIVO o PENDIENTE) en el mismo cargo.
 */
@ExtendWith(MockitoExtension.class)
class DesignacionServiceTest {

    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoRepository concursoRepository;

    private DesignacionService service;
    private Cargo cargo;
    private Docente docente;

    @BeforeEach
    void setUp() {
        service = new DesignacionService(designacionRepository, concursoRepository);

        docente = new Docente();
        docente.setId(7L);

        cargo = new Cargo();
        cargo.setId(5L);

        Concurso concurso = new Concurso();
        concurso.setId(1L);
        concurso.setCargo(cargo);
        lenient().when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
    }

    private Designacion designacion(EstadoDesignacion estado, Long concursoId) {
        Designacion designacion = new Designacion();
        designacion.setDocente(docente);
        designacion.setEstadoDesignacion(estado);
        designacion.setConcursoId(concursoId);
        designacion.setFechaInicioEfectiva(LocalDate.now());
        designacion.setOrigenVacancia("Licencia");
        return designacion;
    }

    @Test
    void segundaDesignacionVigenteEnElMismoCargoSeRechaza() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.PENDIENTE, 1L)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class,
                () -> service.alta(designacion(EstadoDesignacion.ACTIVO, 1L)));
        assertEquals("El docente ya posee una designación vigente para este cargo.", ex.getMessage());
    }

    @Test
    void designacionHistoricaFinalizadaNoBloquea() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.FINALIZADO, 1L)));
        assertDoesNotThrow(() -> service.alta(designacion(EstadoDesignacion.ACTIVO, 1L)));
    }

    @Test
    void designacionVigenteEliminadaNoBloquea() {
        Designacion eliminada = designacion(EstadoDesignacion.ACTIVO, 1L);
        eliminada.setEliminado(true);
        cargo.setDesignaciones(List.of(eliminada));
        assertDoesNotThrow(() -> service.alta(designacion(EstadoDesignacion.ACTIVO, 1L)));
    }

    @Test
    void altaManualSinConcursoNoSeValida() {
        cargo.setDesignaciones(List.of(designacion(EstadoDesignacion.ACTIVO, 1L)));
        assertDoesNotThrow(() -> service.alta(designacion(EstadoDesignacion.ACTIVO, null)));
    }
}
