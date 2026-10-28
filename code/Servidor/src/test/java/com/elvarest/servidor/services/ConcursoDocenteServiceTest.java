package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DocenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;

/**
 * Reglas de postulación validadas en el servidor: alta (§3.2), horas (§3.3) y ciclo de estados (§3.4 a §3.6).
 */
@ExtendWith(MockitoExtension.class)
class ConcursoDocenteServiceTest {

    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;
    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private DocenteRepository docenteRepository;

    private ConcursoDocenteService service;
    private Cargo cargo;
    private Concurso concurso;
    private Docente docente;
    private final List<ConcursoDocente> postulaciones = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ConcursoDocenteService(concursoDocenteRepository, concursoRepository, docenteRepository);

        cargo = new Cargo();
        cargo.setId(5L);
        cargo.setCargaHoraria(20);

        concurso = new Concurso();
        concurso.setId(1L);
        concurso.setDescripcion("Matemática 1° año");
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        concurso.setCargo(cargo);
        concurso.setHorasTotales(10);

        docente = docente(7L, true);

        lenient().when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        lenient().when(docenteRepository.findById(7L)).thenReturn(Optional.of(docente));
        lenient().when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(1L)).thenReturn(postulaciones);
    }

    private Docente docente(Long id, boolean activo) {
        Docente nuevo = new Docente();
        nuevo.setId(id);
        nuevo.setActivo(activo);
        return nuevo;
    }

    private ConcursoDocente postulacion(Long id, Docente docenteDe, Integer horas, EstadoPostulacion estado) {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(id);
        postulacion.setConcurso(concurso);
        postulacion.setDocente(docenteDe);
        postulacion.setHorasElegidas(horas);
        postulacion.setEstadoPostulacion(estado);
        return postulacion;
    }

    /** Lo que envía el cliente al registrar una toma de horas. */
    private ConcursoDocente pedido(Integer horas) {
        return postulacion(null, docente, horas, null);
    }

    /** Copia de una postulación guardada, como la envía el cliente en un PUT. */
    private ConcursoDocente copia(ConcursoDocente original) {
        ConcursoDocente copia = postulacion(original.getId(), original.getDocente(),
                original.getHorasElegidas(), original.getEstadoPostulacion());
        copia.setGanador(original.isGanador());
        return copia;
    }

    private void guardada(ConcursoDocente postulacion) {
        postulaciones.add(postulacion);
        lenient().when(concursoDocenteRepository.findById(postulacion.getId())).thenReturn(Optional.of(postulacion));
    }

    private void assertRechaza(String mensaje, org.junit.jupiter.api.function.Executable accion) {
        ErrorServiceException ex = assertThrows(ErrorServiceException.class, accion);
        assertEquals(mensaje, ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Alta (§3.2)
    // ------------------------------------------------------------------

    @Test
    void altaValidaFijaLosValoresInicialesDelServidor() {
        ConcursoDocente pedido = pedido(4);
        pedido.setGanador(true);
        pedido.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);

        assertDoesNotThrow(() -> service.alta(pedido));

        assertEquals(EstadoPostulacion.PENDIENTE_CERTIFICADOS, pedido.getEstadoPostulacion());
        assertFalse(pedido.isGanador());
        assertEquals(LocalDate.now(), pedido.getFechaEmision());
    }

    @Test
    void altaEnConcursoFinalizadoSeRechaza() {
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        assertRechaza("El concurso está cerrado: no se pueden registrar tomas de horas.", () -> service.alta(pedido(4)));
    }

    @Test
    void altaDeDocenteInactivoSeRechaza() {
        docente.setActivo(false);
        assertRechaza("Solo se pueden registrar docentes activos.", () -> service.alta(pedido(4)));
    }

    @Test
    void altaDeDocenteConTomaVigenteEnElConcursoSeRechaza() {
        guardada(postulacion(20L, docente, 2, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION));
        assertRechaza("El docente ya tiene una toma de horas vigente en este concurso.", () -> service.alta(pedido(4)));
    }

    @Test
    void altaDeDocenteConPostulacionCanceladaSePermite() {
        guardada(postulacion(20L, docente, 2, EstadoPostulacion.CANCELADO_INEFECTIVO));
        assertDoesNotThrow(() -> service.alta(pedido(4)));
    }

    @Test
    void altaDeDocenteConDesignacionActivaEnElCargoSeRechaza() {
        Designacion activa = new Designacion();
        activa.setDocente(docente);
        activa.setEstadoDesignacion(EstadoDesignacion.ACTIVO);
        cargo.setDesignaciones(List.of(activa));

        assertRechaza("El docente tiene una designación efectiva vigente en este mismo cargo, materia y curso: "
                + "no puede postularse a su propio reemplazo.", () -> service.alta(pedido(4)));
    }

    @Test
    void altaEnRellamadoDeDocenteDesignadoEnElOrigenSeRechaza() {
        Concurso origen = new Concurso();
        origen.setId(2L);
        concurso.setEsRellamado(true);
        concurso.setConcursoOrigenId(2L);
        lenient().when(concursoRepository.findById(2L)).thenReturn(Optional.of(origen));

        Designacion delOrigen = new Designacion();
        delOrigen.setDocente(docente);
        delOrigen.setEstadoDesignacion(EstadoDesignacion.ACTIVO);
        delOrigen.setConcursoId(2L);
        cargo.setDesignaciones(List.of(delOrigen));

        assertRechaza("El docente ya obtuvo una designación efectiva en el concurso origen para este mismo cargo, "
                + "materia y curso: no puede volver a seleccionarse en el rellamado.", () -> service.alta(pedido(4)));
    }

    @Test
    void altaSinHorasSeRechaza() {
        assertRechaza("Indique la cantidad de horas tomadas (mínimo 1).", () -> service.alta(pedido(0)));
    }

    @Test
    void altaConMasHorasQueLasDisponiblesSeRechaza() {
        // 10 totales − 6 asignadas − 2 transferidas = 2 disponibles; la cancelada no reserva horas.
        concurso.setHorasTransferidas(2);
        guardada(postulacion(20L, docente(8L, true), 6, EstadoPostulacion.PENDIENTE_CERTIFICADOS));
        guardada(postulacion(21L, docente(9L, true), 5, EstadoPostulacion.CANCELADO_INEFECTIVO));

        assertRechaza("Solo hay 2 horas disponibles en el concurso.", () -> service.alta(pedido(3)));
        assertDoesNotThrow(() -> service.alta(pedido(2)));
    }

    // ------------------------------------------------------------------
    // Cargos de preceptor (§3.2)
    // ------------------------------------------------------------------

    private Docente preceptor(Long id) {
        Docente nuevo = docente(id, true);
        nuevo.setTipo(TipoCargo.PRECEPTOR);
        lenient().when(docenteRepository.findById(id)).thenReturn(Optional.of(nuevo));
        return nuevo;
    }

    private ConcursoDocente pedidoDe(Docente docenteDe, Integer horas) {
        return postulacion(null, docenteDe, horas, null);
    }

    @Test
    void preceptorEnCargoDocenteSeRechaza() {
        assertRechaza("Este cargo es docente: no se pueden postular preceptores.",
                () -> service.alta(pedidoDe(preceptor(8L), 4)));
    }

    @Test
    void docenteEnCargoDePreceptorSeRechaza() {
        cargo.setTipoCargo(TipoCargo.PRECEPTOR);
        assertRechaza("Este cargo es de preceptor: solo se pueden postular preceptores.",
                () -> service.alta(pedido(4)));
    }

    @Test
    void preceptorTomaTodasLasHorasDisponibles() {
        cargo.setTipoCargo(TipoCargo.PRECEPTOR);
        concurso.setHorasTransferidas(2);
        ConcursoDocente pedido = pedidoDe(preceptor(8L), 1);

        assertDoesNotThrow(() -> service.alta(pedido));

        assertEquals(8, pedido.getHorasElegidas());
    }

    @Test
    void segundaPersonaEnConcursoDePreceptorSeRechaza() {
        cargo.setTipoCargo(TipoCargo.PRECEPTOR);
        guardada(postulacion(20L, preceptor(8L), 10, EstadoPostulacion.PENDIENTE_CERTIFICADOS));

        assertRechaza("El concurso de preceptor admite una sola persona y ya tiene una postulación vigente.",
                () -> service.alta(pedidoDe(preceptor(9L), null)));
    }

    @Test
    void otraPersonaTrasUnaCancelacionEnConcursoDePreceptorSePermite() {
        cargo.setTipoCargo(TipoCargo.PRECEPTOR);
        guardada(postulacion(20L, preceptor(8L), 10, EstadoPostulacion.CANCELADO_INEFECTIVO));
        ConcursoDocente pedido = pedidoDe(preceptor(9L), null);

        assertDoesNotThrow(() -> service.alta(pedido));

        assertEquals(10, pedido.getHorasElegidas());
    }

    @Test
    void horasDeUnPreceptorNoSeModifican() {
        cargo.setTipoCargo(TipoCargo.PRECEPTOR);
        ConcursoDocente pendiente = postulacion(20L, preceptor(8L), 10, EstadoPostulacion.PENDIENTE_CERTIFICADOS);
        guardada(pendiente);
        ConcursoDocente cambio = copia(pendiente);
        cambio.setHorasElegidas(5);

        assertRechaza("En un concurso de preceptor no se modifican horas: "
                + "el preceptor toma todas las horas del concurso.", () -> service.modificar(20L, cambio));
    }

    // ------------------------------------------------------------------
    // Ciclo de estados (§3.4 a §3.6)
    // ------------------------------------------------------------------

    @Test
    void transicionesValidasSePermiten() {
        ConcursoDocente pendiente = postulacion(20L, docente, 4, EstadoPostulacion.PENDIENTE_CERTIFICADOS);
        guardada(pendiente);

        ConcursoDocente aprobada = copia(pendiente);
        aprobada.setEstadoPostulacion(EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION);
        assertDoesNotThrow(() -> service.modificar(20L, aprobada));

        ConcursoDocente rechazada = copia(pendiente);
        rechazada.setEstadoPostulacion(EstadoPostulacion.CANCELADO_INEFECTIVO);
        assertDoesNotThrow(() -> service.modificar(20L, rechazada));
    }

    @Test
    void saltearLaValidacionDeCertificadosSeRechaza() {
        ConcursoDocente pendiente = postulacion(20L, docente, 4, EstadoPostulacion.PENDIENTE_CERTIFICADOS);
        guardada(pendiente);
        ConcursoDocente definitiva = copia(pendiente);
        definitiva.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);

        assertRechaza("No se puede pasar una postulación de «Pendiente de certificados» a «Designación definitiva».",
                () -> service.modificar(20L, definitiva));
    }

    @Test
    void unaPostulacionCanceladaNoSeReactiva() {
        ConcursoDocente cancelada = postulacion(20L, docente, 4, EstadoPostulacion.CANCELADO_INEFECTIVO);
        guardada(cancelada);
        ConcursoDocente reactivada = copia(cancelada);
        reactivada.setEstadoPostulacion(EstadoPostulacion.PENDIENTE_CERTIFICADOS);

        assertRechaza("No se puede pasar una postulación de «Cancelado / inefectivo» a «Pendiente de certificados».",
                () -> service.modificar(20L, reactivada));
    }

    @Test
    void cambioDeEstadoConConcursoCerradoSeRechaza() {
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        ConcursoDocente pendiente = postulacion(20L, docente, 4, EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION);
        guardada(pendiente);
        ConcursoDocente definitiva = copia(pendiente);
        definitiva.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);

        assertRechaza("El concurso está cerrado: no se puede cambiar el estado de la postulación.",
                () -> service.modificar(20L, definitiva));
    }

    @Test
    void ganadorLegacyEnConcursoFinalizadoSigueFuncionando() {
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        ConcursoDocente historica = postulacion(20L, docente, null, null);
        guardada(historica);
        ConcursoDocente ganadora = copia(historica);
        ganadora.setGanador(true);

        assertDoesNotThrow(() -> service.modificar(20L, ganadora));
    }

    @Test
    void noSePuedeCambiarElDocenteDeUnaPostulacion() {
        ConcursoDocente pendiente = postulacion(20L, docente, 4, EstadoPostulacion.PENDIENTE_CERTIFICADOS);
        guardada(pendiente);
        ConcursoDocente otroDocente = copia(pendiente);
        otroDocente.setDocente(docente(8L, true));

        assertRechaza("No se puede cambiar el concurso ni el docente de una postulación.",
                () -> service.modificar(20L, otroDocente));
    }

    // ------------------------------------------------------------------
    // Modificación de horas (§3.3)
    // ------------------------------------------------------------------

    @Test
    void horasHastaElMaximoDisponibleSePermiten() {
        // 10 totales − 4 asignadas = 6 disponibles: máximo 4 + 6 = 10.
        ConcursoDocente pendiente = postulacion(20L, docente, 4, EstadoPostulacion.PENDIENTE_CERTIFICADOS);
        guardada(pendiente);

        ConcursoDocente diez = copia(pendiente);
        diez.setHorasElegidas(10);
        assertDoesNotThrow(() -> service.modificar(20L, diez));

        ConcursoDocente once = copia(pendiente);
        once.setHorasElegidas(11);
        assertRechaza("El máximo permitido es 10 h (horas actuales del docente + horas disponibles del concurso).",
                () -> service.modificar(20L, once));
    }

    @Test
    void horasDeUnaDesignacionDefinitivaNoSeModifican() {
        ConcursoDocente definitiva = postulacion(20L, docente, 4, EstadoPostulacion.DESIGNACION_DEFINITIVA);
        guardada(definitiva);
        ConcursoDocente cambio = copia(definitiva);
        cambio.setHorasElegidas(5);

        assertRechaza("Solo se pueden modificar horas de postulaciones pendientes de certificados o de posesión.",
                () -> service.modificar(20L, cambio));
    }
}
