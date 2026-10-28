package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
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
 * Reglas de eliminación de concursos:
 * - Regla 1: EN_PROCESO con designaciones pendientes de certificados o de toma de posesión:
 *   postulaciones no canceladas (las canceladas ya no bloquean) o designaciones en estado PENDIENTE.
 *   Los registros sin estado de postulación se tratan de forma conservadora (bloquean).
 * - Regla 2: FINALIZADO con designaciones efectivas (ACTIVO) que continúan activas.
 */
@ExtendWith(MockitoExtension.class)
class ConcursoServiceTest {

    private static final String MENSAJE_REGLA_1 =
            "No se puede eliminar el concurso porque tiene designaciones pendientes de certificados o de toma de posesión.";
    private static final String MENSAJE_REGLA_2 =
            "No se puede eliminar el concurso porque tiene designaciones efectivas que continúan activas.";

    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;
    @Mock
    private AlertaService alertaService;

    private ConcursoService service;
    private Concurso concurso;

    @BeforeEach
    void setUp() {
        service = new ConcursoService(concursoRepository, designacionRepository, concursoDocenteRepository,
                alertaService);
        concurso = new Concurso();
        concurso.setId(1L);
        concurso.setDescripcion("Matemática 2026");
    }

    private Designacion designacion(EstadoDesignacion estado, Long concursoId, String origenVacancia) {
        Designacion designacion = new Designacion();
        designacion.setId(10L);
        designacion.setEstadoDesignacion(estado);
        designacion.setConcursoId(concursoId);
        designacion.setOrigenVacancia(origenVacancia);
        return designacion;
    }

    private ConcursoDocente postulacion(boolean ganador, EstadoPostulacion estadoPostulacion) {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(20L);
        postulacion.setConcurso(concurso);
        postulacion.setGanador(ganador);
        postulacion.setEstadoPostulacion(estadoPostulacion);
        return postulacion;
    }

    @Test
    void enProcesoConPostulacionSinEstadoRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(postulacion(false, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_1, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionPendienteDeCertificadosRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_1, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionPendienteDePosesionRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_1, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionCanceladaPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.CANCELADO_INEFECTIVO)));
        when(designacionRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void enProcesoConDesignacionPendienteVinculadaPorIdRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(List.of(designacion(EstadoDesignacion.PENDIENTE, 1L, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_1, ex.getMessage());
    }

    @Test
    void enProcesoConDesignacionPendienteVinculadaPorTextoRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(
                List.of(designacion(EstadoDesignacion.PENDIENTE, null, "Concurso: Matemática 2026")));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_1, ex.getMessage());
    }

    @Test
    void finalizadoConDesignacionActivaRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(designacionRepository.findAll()).thenReturn(List.of(designacion(EstadoDesignacion.ACTIVO, 1L, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_REGLA_2, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionGanadoraPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(postulacion(true, null)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void enProcesoConDesignacionDeOtroConcursoPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(
                List.of(designacion(EstadoDesignacion.PENDIENTE, 99L, "Concurso: Otro concurso")));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void finalizadoSinDesignacionesActivasPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(designacionRepository.findAll()).thenReturn(List.of(designacion(EstadoDesignacion.FINALIZADO, 1L, null)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void publicadoConDesignacionActivaPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void concursoInexistentePermiteEliminacion() {
        when(concursoRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void concursoYaEliminadoPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        concurso.setEliminado(true);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }
}
