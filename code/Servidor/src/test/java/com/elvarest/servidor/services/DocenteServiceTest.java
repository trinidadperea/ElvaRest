package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import com.elvarest.servidor.repositories.DocenteRepository;
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
 * Reglas de eliminación de docentes:
 * - Cualquier designación asociada bloquea la baja, sin importar su estado (activa, pendiente o
 *   finalizada): se conserva el historial.
 * - Las postulaciones vigentes sin designación definitiva también la bloquean (las canceladas no).
 *   Los registros sin estado de postulación se tratan de forma conservadora (bloquean).
 */
@ExtendWith(MockitoExtension.class)
class DocenteServiceTest {

    private static final String MENSAJE_DESIGNACIONES =
            "No se puede eliminar el docente porque tiene designaciones asociadas.";
    private static final String MENSAJE_POSTULACIONES =
            "No se puede eliminar el docente porque tiene designaciones pendientes de certificados o de toma de posesión.";

    @Mock
    private DocenteRepository docenteRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;

    private DocenteService service;
    private Docente docente;

    @BeforeEach
    void setUp() {
        service = new DocenteService(docenteRepository, designacionRepository, concursoDocenteRepository);
        docente = new Docente();
        docente.setId(1L);
    }

    private Designacion designacionDe(Docente titular, EstadoDesignacion estado, boolean eliminada) {
        Designacion designacion = new Designacion();
        designacion.setId(10L);
        designacion.setDocente(titular);
        designacion.setEstadoDesignacion(estado);
        designacion.setEliminado(eliminada);
        return designacion;
    }

    private ConcursoDocente postulacionDe(Docente postulante, boolean ganador, EstadoPostulacion estadoPostulacion) {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(20L);
        postulacion.setDocente(postulante);
        postulacion.setGanador(ganador);
        postulacion.setEstadoPostulacion(estadoPostulacion);
        return postulacion;
    }

    @Test
    void docenteConDesignacionActivaRechazaEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll())
                .thenReturn(List.of(designacionDe(docente, EstadoDesignacion.ACTIVO, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void docenteConDesignacionPendienteRechazaEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll())
                .thenReturn(List.of(designacionDe(docente, EstadoDesignacion.PENDIENTE, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void docenteConDesignacionFinalizadaRechazaEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll())
                .thenReturn(List.of(designacionDe(docente, EstadoDesignacion.FINALIZADO, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void docenteConPostulacionSinEstadoRechazaEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(postulacionDe(docente, false, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void docenteConPostulacionPendienteDeCertificadosRechazaEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacionDe(docente, false, EstadoPostulacion.PENDIENTE_CERTIFICADOS)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void docenteConPostulacionPendienteDePosesionRechazaEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacionDe(docente, false, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void docenteConPostulacionCanceladaPermiteEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacionDe(docente, false, EstadoPostulacion.CANCELADO_INEFECTIVO)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void docenteSinRelacionesPermiteEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void docenteConDesignacionFinalizadaDeOtroDocenteYPostulacionGanadoraPermiteEliminacion() {
        Docente otro = new Docente();
        otro.setId(99L);
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll())
                .thenReturn(List.of(designacionDe(otro, EstadoDesignacion.FINALIZADO, false)));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(postulacionDe(docente, true, null)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void docenteConDesignacionActivaDeOtroDocentePermiteEliminacion() {
        Docente otro = new Docente();
        otro.setId(99L);
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll())
                .thenReturn(List.of(designacionDe(otro, EstadoDesignacion.ACTIVO, false)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void designacionesEliminadasNoBloqueanEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));
        when(designacionRepository.findAll()).thenReturn(List.of(
                designacionDe(docente, EstadoDesignacion.ACTIVO, true),
                designacionDe(docente, EstadoDesignacion.PENDIENTE, true)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void docenteInexistentePermiteEliminacion() {
        when(docenteRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void docenteYaEliminadoPermiteEliminacion() {
        docente.setEliminado(true);
        when(docenteRepository.findById(1L)).thenReturn(Optional.of(docente));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }
}
