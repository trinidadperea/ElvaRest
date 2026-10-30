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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de designaciones:
 * - §4: un docente no puede tener dos designaciones vigentes (ACTIVO o PENDIENTE) en el mismo cargo.
 * - El estado se deriva de la fecha de fin definitiva al guardar y en la revisión diaria.
 */
@ExtendWith(MockitoExtension.class)
class DesignacionServiceTest {

    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private AlertaService alertaService;

    private DesignacionService service;
    private Cargo cargo;
    private Docente docente;

    @BeforeEach
    void setUp() {
        service = new DesignacionService(designacionRepository, concursoRepository, alertaService);

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

    /** Designación suelta (sin concurso) para probar la derivación del estado por fecha de fin. */
    private Designacion designacionConFechas(EstadoDesignacion estado, LocalDate fechaFin) {
        Designacion designacion = new Designacion();
        designacion.setEstadoDesignacion(estado);
        designacion.setFechaInicioEfectiva(LocalDate.now().minusMonths(2));
        designacion.setFechaFinDefinitiva(fechaFin);
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

    @Test
    void alEliminarLaDesignacionSeResuelveSuAlertaEnElActo() throws Exception {
        // Solo las PENDIENTE residuales se eliminan: las efectivas son historial.
        Designacion guardada = designacion(EstadoDesignacion.PENDIENTE, null);
        guardada.setId(15L);
        when(designacionRepository.findById(15L)).thenReturn(Optional.of(guardada));

        assertTrue(service.bajaLogica(15L));

        assertTrue(guardada.getEliminado(), "La baja es lógica: la designación se conserva.");
        verify(alertaService).evaluarVencimientoDesignacion(guardada);
    }

    @Test
    void unaDesignacionPendienteSeElimina() {
        Designacion pendiente = designacion(EstadoDesignacion.PENDIENTE, null);
        pendiente.setId(15L);
        when(designacionRepository.findById(15L)).thenReturn(Optional.of(pendiente));

        assertDoesNotThrow(() -> service.validarEliminacion(15L));
    }

    @Test
    void unaDesignacionActivaNoSeElimina() {
        Designacion activa = designacion(EstadoDesignacion.ACTIVO, null);
        activa.setId(15L);
        when(designacionRepository.findById(15L)).thenReturn(Optional.of(activa));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(15L));
        assertEquals("No se puede eliminar una designación activa: "
                + "cárguele la fecha de fin definitiva para finalizarla.", ex.getMessage());
    }

    @Test
    void unaDesignacionFinalizadaNoSeElimina() {
        Designacion finalizada = designacion(EstadoDesignacion.FINALIZADO, null);
        finalizada.setId(15L);
        when(designacionRepository.findById(15L)).thenReturn(Optional.of(finalizada));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(15L));
        assertEquals("No se puede eliminar una designación finalizada: forma parte del historial.",
                ex.getMessage());
    }

    @Test
    void unaDesignacionHistoricaSinEstadoNoSeElimina() {
        Designacion historica = designacion(null, null);
        historica.setId(15L);
        when(designacionRepository.findById(15L)).thenReturn(Optional.of(historica));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(15L));
        assertEquals("No se puede eliminar una designación histórica: forma parte del historial.",
                ex.getMessage());
    }

    @Test
    void unaDesignacionYaEliminadaNoSeValida() {
        Designacion eliminada = designacion(EstadoDesignacion.FINALIZADO, null);
        eliminada.setId(15L);
        eliminada.setEliminado(true);
        when(designacionRepository.findById(15L)).thenReturn(Optional.of(eliminada));

        assertDoesNotThrow(() -> service.validarEliminacion(15L));
    }

    @Test
    void sinFechaDeFinLaDesignacionQuedaActiva() throws Exception {
        Designacion designacion = designacionConFechas(EstadoDesignacion.ACTIVO, null);

        service.alta(designacion);

        assertEquals(EstadoDesignacion.ACTIVO, designacion.getEstadoDesignacion());
    }

    @Test
    void conFechaDeFinFuturaLaDesignacionQuedaActiva() throws Exception {
        Designacion designacion = designacionConFechas(null, LocalDate.now().plusDays(30));

        service.alta(designacion);

        assertEquals(EstadoDesignacion.ACTIVO, designacion.getEstadoDesignacion());
    }

    @Test
    void conLaFechaDeFinAlcanzadaLaDesignacionQuedaFinalizada() throws Exception {
        Designacion venceHoy = designacionConFechas(null, LocalDate.now());
        service.alta(venceHoy);
        assertEquals(EstadoDesignacion.FINALIZADO, venceHoy.getEstadoDesignacion());

        Designacion vencida = designacionConFechas(null, LocalDate.now().minusDays(3));
        service.alta(vencida);
        assertEquals(EstadoDesignacion.FINALIZADO, vencida.getEstadoDesignacion());
    }

    @Test
    void laDesignacionPendienteSeConservaAunqueLaFechaDeFinYaPaso() throws Exception {
        Designacion designacion = designacionConFechas(EstadoDesignacion.PENDIENTE, LocalDate.now().minusDays(3));

        service.alta(designacion);

        assertEquals(EstadoDesignacion.PENDIENTE, designacion.getEstadoDesignacion(),
                "La toma de posesión la maneja el flujo del concurso, no las fechas.");
    }

    @Test
    void laFechaDeFinAnteriorAlInicioSeRechaza() {
        Designacion designacion = designacionConFechas(null, LocalDate.now().plusDays(1));
        designacion.setFechaInicioEfectiva(LocalDate.now().plusDays(5));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(designacion));

        assertEquals("La fecha de fin definitiva no puede ser anterior a la fecha de inicio efectiva.",
                ex.getMessage());
    }

    @Test
    void laRevisionDiariaFinalizaSoloLasDesignacionesActivasVencidas() throws Exception {
        Designacion vencida = designacionConFechas(EstadoDesignacion.ACTIVO, LocalDate.now().minusDays(1));
        vencida.setId(1L);
        Designacion vigente = designacionConFechas(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(10));
        vigente.setId(2L);
        Designacion sinFechaDeFin = designacionConFechas(EstadoDesignacion.ACTIVO, null);
        sinFechaDeFin.setId(3L);
        Designacion pendiente = designacionConFechas(EstadoDesignacion.PENDIENTE, LocalDate.now().minusDays(1));
        pendiente.setId(4L);
        Designacion eliminada = designacionConFechas(EstadoDesignacion.ACTIVO, LocalDate.now().minusDays(1));
        eliminada.setId(5L);
        eliminada.setEliminado(true);
        when(designacionRepository.findAll())
                .thenReturn(List.of(vencida, vigente, sinFechaDeFin, pendiente, eliminada));

        assertEquals(1, service.finalizarVencidas());

        assertEquals(EstadoDesignacion.FINALIZADO, vencida.getEstadoDesignacion());
        assertEquals(EstadoDesignacion.ACTIVO, vigente.getEstadoDesignacion());
        assertEquals(EstadoDesignacion.ACTIVO, sinFechaDeFin.getEstadoDesignacion());
        assertEquals(EstadoDesignacion.PENDIENTE, pendiente.getEstadoDesignacion());
        assertEquals(EstadoDesignacion.ACTIVO, eliminada.getEstadoDesignacion());
        verify(designacionRepository).save(vencida);
        verify(designacionRepository, never()).save(vigente);
    }
}
