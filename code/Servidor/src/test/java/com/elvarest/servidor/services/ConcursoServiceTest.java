package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.CaracterDesignacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de eliminación de concursos: un concurso con actividad no se elimina.
 * - Postulaciones: bloquea cualquier toma de horas no eliminada ni cancelada; las declaradas
 *   inefectivas no cuentan, así un concurso con todas sus postulaciones canceladas se puede borrar.
 * - Designaciones: bloquea cualquier designación vinculada, sin importar su estado (activa,
 *   pendiente, finalizada o histórica sin estado), porque conserva el historial del concurso.
 */
@ExtendWith(MockitoExtension.class)
class ConcursoServiceTest {

    private static final String MENSAJE_POSTULACIONES =
            "No se puede eliminar el concurso porque tiene postulaciones asociadas.";
    private static final String MENSAJE_DESIGNACIONES =
            "No se puede eliminar el concurso porque tiene designaciones asociadas.";

    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;
    @Mock
    private AlertaService alertaService;
    @Mock
    private CargoRepository cargoRepository;

    private ConcursoService service;
    private Concurso concurso;

    @BeforeEach
    void setUp() {
        service = new ConcursoService(concursoRepository, designacionRepository, concursoDocenteRepository,
                alertaService, cargoRepository);
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
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionPendienteDeCertificadosRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionPendienteDePosesionRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionGanadoraRechazaEliminacion() {
        // Ganar no libera el concurso: la postulación sigue siendo la toma de horas que lo justifica.
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(true, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void publicadoConPostulacionRechazaEliminacion() {
        // La regla no depende del estado del concurso: cualquiera con actividad queda bloqueado.
        concurso.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    @Test
    void enProcesoConPostulacionEliminadaPermiteEliminacion() {
        ConcursoDocente eliminada = postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS);
        eliminada.setEliminado(true);
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(eliminada));
        when(designacionRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void enProcesoConTodasLasPostulacionesCanceladasPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(
                postulacion(false, EstadoPostulacion.CANCELADO_INEFECTIVO),
                postulacion(false, EstadoPostulacion.CANCELADO_INEFECTIVO)));
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
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void enProcesoConDesignacionPendienteVinculadaPorTextoRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(
                List.of(designacion(EstadoDesignacion.PENDIENTE, null, "Concurso: Matemática 2026")));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void finalizadoConDesignacionActivaRechazaEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(List.of(designacion(EstadoDesignacion.ACTIVO, 1L, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void finalizadoConSuUnicaDesignacionFinalizadaRechazaEliminacion() {
        // El historial de la designación apunta al concurso: borrarlo dejaría la designación huérfana.
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll())
                .thenReturn(List.of(designacion(EstadoDesignacion.FINALIZADO, 1L, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
    }

    @Test
    void designacionSinEstadoRechazaEliminacion() {
        // Registros históricos sin estado persistido: se tratan de forma conservadora (§9).
        concurso.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(List.of(designacion(null, 1L, null)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_DESIGNACIONES, ex.getMessage());
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
    void publicadoSinActividadPermiteEliminacion() {
        concurso.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(designacionRepository.findAll()).thenReturn(List.of());

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

    @Test
    void bajaLogicaPropagaElMensajeDeLaRegla() {
        // La regla se aplica en el servicio: el mensaje llega al usuario en lugar de "Error de Sistemas".
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.bajaLogica(1L));
        assertEquals(MENSAJE_POSTULACIONES, ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Horas totales vs. carga horaria del cargo
    // ------------------------------------------------------------------

    private void conCargoDe(int cargaHoraria, Integer horasTotales) {
        Cargo cargo = new Cargo();
        cargo.setId(5L);
        cargo.setCargaHoraria(cargaHoraria);
        when(cargoRepository.findById(5L)).thenReturn(Optional.of(cargo));
        Cargo enviado = new Cargo();
        enviado.setId(5L);
        concurso.setCargo(enviado);
        concurso.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        concurso.setHorasTotales(horasTotales);
    }

    @Test
    void horasTotalesMayoresALaCargaDelCargoNoSePermiten() {
        conCargoDe(10, 12);

        ErrorServiceException e = assertThrows(ErrorServiceException.class, () -> service.validar(concurso));
        assertEquals("Las horas totales del concurso (12 h) no pueden superar la carga horaria del cargo (10 h).",
                e.getMessage());
    }

    @Test
    void horasTotalesIgualesALaCargaDelCargoSePermiten() {
        conCargoDe(10, 10);

        assertDoesNotThrow(() -> service.validar(concurso));
    }

    @Test
    void cargoSinCargaHorariaNoLimitaLasHoras() {
        conCargoDe(0, 12);

        assertDoesNotThrow(() -> service.validar(concurso));
    }

    // ------------------------------------------------------------------
    // Cierre: pendientes de certificados
    // ------------------------------------------------------------------

    /** El cierre normal parte de un concurso abierto guardado y guarda el cambio con modificar. */
    private void conConcursoGuardado(EstadoConcurso estadoGuardado) {
        Concurso guardado = new Concurso();
        guardado.setId(1L);
        guardado.setEstadoConcurso(estadoGuardado);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(guardado));
        lenient().when(concursoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void cierreConDocentesPendientesDeCertificadosSeRechaza() {
        conConcursoGuardado(EstadoConcurso.EN_PROCESO);
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(
                postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS),
                postulacion(false, EstadoPostulacion.PENDIENTE_CERTIFICADOS)));
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.modificar(1L, concurso));
        assertEquals("No se puede cerrar el concurso: hay 2 docente(s) pendientes de validación de certificados.",
                ex.getMessage());
        verify(concursoRepository, never()).save(any());
    }

    @Test
    void cierreSinPendientesSeGuarda() {
        conConcursoGuardado(EstadoConcurso.EN_PROCESO);
        when(concursoDocenteRepository.findAll())
                .thenReturn(List.of(postulacion(true, EstadoPostulacion.DESIGNACION_DEFINITIVA)));
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);

        assertDoesNotThrow(() -> service.modificar(1L, concurso));
        verify(concursoRepository).save(concurso);
    }

    @Test
    void modificarUnConcursoQueYaEstabaCerradoNoRepiteLaValidacionDePendientes() {
        // Una corrección sobre un concurso ya cerrado no es un cierre nuevo: no bloquea.
        conConcursoGuardado(EstadoConcurso.FINALIZADO);
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);

        assertDoesNotThrow(() -> service.modificar(1L, concurso));
    }

    // ------------------------------------------------------------------
    // Rellamado: reglas y transferencia de horas
    // ------------------------------------------------------------------

    private Concurso origenCerrado(Integer horasTotales) {
        Cargo cargo = new Cargo();
        cargo.setId(5L);
        cargo.setCargaHoraria(10);
        Concurso origen = new Concurso();
        origen.setId(3L);
        origen.setDescripcion("Matemática 2026");
        origen.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        origen.setHorasTotales(horasTotales);
        origen.setHorasTransferidas(null);
        origen.setCargo(cargo);
        origen.setCaracterDesignacion(CaracterDesignacion.SUPLENTE);
        origen.setOrigenVacancia("Licencia");
        return origen;
    }

    /** Postulación guardada en el concurso indicado, con las horas que tomó. */
    private ConcursoDocente postulacionDe(Long concursoId, Integer horas) {
        Concurso delConcurso = new Concurso();
        delConcurso.setId(concursoId);
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(30L + horas);
        postulacion.setConcurso(delConcurso);
        postulacion.setHorasElegidas(horas);
        postulacion.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);
        return postulacion;
    }

    private Concurso pedidoDeRellamado() {
        Concurso rellamado = new Concurso();
        rellamado.setConcursoOrigenId(3L);
        rellamado.setEstadoConcurso(EstadoConcurso.PUBLICADO);
        rellamado.setFechaCierre(LocalDate.now());
        return rellamado;
    }

    @Test
    void rellamadoHeredaElOrigenYTransfiereLasHorasRemanentes() {
        Concurso origen = origenCerrado(10);
        when(concursoRepository.findById(3L)).thenReturn(Optional.of(origen));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(postulacionDe(3L, 4)));
        when(concursoRepository.findAll()).thenReturn(List.of(origen));
        when(concursoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(cargoRepository.findById(5L)).thenReturn(Optional.of(origen.getCargo()));

        Concurso rellamado = pedidoDeRellamado();
        assertDoesNotThrow(() -> service.alta(rellamado));

        // 10 h del origen − 4 h ya tomadas = 6 h que pasan al rellamado.
        assertEquals(6, rellamado.getHorasTotales());
        assertEquals(6, origen.getHorasTransferidas());
        assertTrue(rellamado.getEsRellamado());
        assertEquals(origen.getCargo(), rellamado.getCargo());
        assertEquals(CaracterDesignacion.SUPLENTE, rellamado.getCaracterDesignacion());
        assertEquals("Licencia", rellamado.getOrigenVacancia());
        assertEquals("Rellamado – Matemática 2026", rellamado.getDescripcion());
    }

    @Test
    void rellamadoDeUnOrigenSinCaracterNoExigeElegirlo() {
        // El carácter se hereda: si el origen no lo tiene, se completa al confirmar la toma de posesión.
        Concurso origen = origenCerrado(10);
        origen.setCaracterDesignacion(null);
        when(concursoRepository.findById(3L)).thenReturn(Optional.of(origen));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of());
        when(concursoRepository.findAll()).thenReturn(List.of(origen));
        when(concursoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(cargoRepository.findById(5L)).thenReturn(Optional.of(origen.getCargo()));

        Concurso rellamado = pedidoDeRellamado();
        assertDoesNotThrow(() -> service.alta(rellamado));
        assertNull(rellamado.getCaracterDesignacion());
        assertEquals(10, rellamado.getHorasTotales());
    }

    @Test
    void rellamadoDeUnOrigenAbiertoSeRechaza() {
        Concurso origen = origenCerrado(10);
        origen.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(3L)).thenReturn(Optional.of(origen));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedidoDeRellamado()));
        assertEquals("El concurso origen debe estar cerrado para generar un rellamado.", ex.getMessage());
    }

    @Test
    void rellamadoSinHorasRemanentesSeRechaza() {
        Concurso origen = origenCerrado(4);
        when(concursoRepository.findById(3L)).thenReturn(Optional.of(origen));
        when(concursoDocenteRepository.findAll()).thenReturn(List.of(postulacionDe(3L, 4)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedidoDeRellamado()));
        assertEquals("El concurso origen no tiene horas remanentes para transferir.", ex.getMessage());
    }

    @Test
    void rellamadoDeUnOrigenInexistenteSeRechaza() {
        when(concursoRepository.findById(3L)).thenReturn(Optional.empty());

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedidoDeRellamado()));
        assertEquals("El concurso origen del rellamado no existe.", ex.getMessage());
    }

    @Test
    void altaDirectaNoTransfiereNiGuardaDosVeces() {
        // Sin concurso origen no hay herencia ni transferencia: un solo save, el del alta.
        conCargoDe(10, 8);
        concurso.setFechaCierre(LocalDate.now().plusDays(5));
        concurso.setCaracterDesignacion(CaracterDesignacion.SUPLENTE);
        when(concursoRepository.findAll()).thenReturn(List.of());
        when(concursoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        assertDoesNotThrow(() -> service.alta(concurso));
        verify(concursoRepository, times(1)).save(concurso);
    }
}
