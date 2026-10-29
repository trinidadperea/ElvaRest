package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.PrioridadAlerta;
import com.elvarest.servidor.repositories.AlertaRepository;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alertas de concurso sin cerrar: se cuentan los días transcurridos desde fechaCierre y se
 * alerta 3 días o más (MEDIA) y 7 días o más (ALTA). Incluye los PUBLICADO con cierre vencido
 * (estado efectivo), porque la transición a EN_PROCESO la persiste el Cliente.
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceConcursoTest {

    private static final Long CONCURSO_ID = 9L;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private CertificadoRepository certificadoRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private CargoRepository cargoRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;

    private AlertaService service;

    @BeforeEach
    void setUp() {
        service = new AlertaService(alertaRepository, certificadoRepository, designacionRepository,
                concursoRepository, concursoDocenteRepository, cargoRepository);
        lenient().when(concursoRepository.findAll()).thenReturn(List.of());
        lenient().when(designacionRepository.findAll()).thenReturn(List.of());
    }

    private Concurso concurso(EstadoConcurso estado, int diasDesdeElCierre) {
        Concurso concurso = new Concurso();
        concurso.setId(CONCURSO_ID);
        concurso.setDescripcion("Matemática 1° año");
        concurso.setEstadoConcurso(estado);
        concurso.setFechaCierre(LocalDate.now().minusDays(diasDesdeElCierre));
        concurso.setEliminado(false);
        return concurso;
    }

    private void guardado(Concurso concurso) {
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
    }

    private void sinAlertasPrevias() {
        lenient().when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>());
    }

    private Alerta alerta(PrioridadAlerta prioridad, boolean leida, String mensaje) {
        Alerta alerta = new Alerta();
        alerta.setId(70L);
        alerta.setConcursoId(CONCURSO_ID);
        alerta.setPrioridad(prioridad);
        alerta.setLeida(leida);
        alerta.setMensaje(mensaje);
        return alerta;
    }

    @Test
    void dosDiasSinCerrarTodaviaNoAlerta() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 2);
        guardado(concurso);
        sinAlertasPrevias();

        assertFalse(service.evaluarConcursoSinCerrar(concurso));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void tresDiasSinCerrarGeneraAlertaMedia() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 4);
        guardado(concurso);
        sinAlertasPrevias();

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        Alerta creada = captor.getValue();
        assertEquals(PrioridadAlerta.MEDIA, creada.getPrioridad());
        assertEquals(CONCURSO_ID, creada.getConcursoId());
        assertFalse(creada.isLeida());
        assertTrue(creada.getMensaje().contains("sigue en proceso"));
        assertTrue(creada.getMensaje().contains(concurso.getFechaCierre().format(FORMATO_FECHA)));
        assertTrue(creada.getMensaje().contains("hace 4 días"));
    }

    @Test
    void sieteDiasSinCerrarGeneraAlertaAlta() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 8);
        guardado(concurso);
        sinAlertasPrevias();

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(PrioridadAlerta.ALTA, captor.getValue().getPrioridad());
    }

    @Test
    void publicadoConCierreVencidoCuentaComoEnProceso() {
        Concurso concurso = concurso(EstadoConcurso.PUBLICADO, 4);
        guardado(concurso);
        sinAlertasPrevias();

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(PrioridadAlerta.MEDIA, captor.getValue().getPrioridad());
    }

    @Test
    void publicadoConCierreFuturoNoAlerta() {
        Concurso concurso = concurso(EstadoConcurso.PUBLICADO, -5);
        guardado(concurso);
        sinAlertasPrevias();

        assertFalse(service.evaluarConcursoSinCerrar(concurso));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void concursoFinalizadoOEliminadoNoAlerta() {
        Concurso finalizado = concurso(EstadoConcurso.FINALIZADO, 10);
        guardado(finalizado);
        assertFalse(service.evaluarConcursoSinCerrar(finalizado));

        Concurso eliminado = concurso(EstadoConcurso.EN_PROCESO, 10);
        eliminado.setEliminado(true);
        guardado(eliminado);
        assertFalse(service.evaluarConcursoSinCerrar(eliminado));

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void alertaVigenteActualizaElMensajeSinReabrir() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 8);
        Alerta alta = alerta(PrioridadAlerta.ALTA, false, "mensaje previo");
        guardado(concurso);
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>(List.of(alta)));

        assertFalse(service.evaluarConcursoSinCerrar(concurso));

        assertFalse(alta.isLeida());
        assertTrue(alta.getMensaje().contains("hace 8 días"));
        verify(alertaRepository).save(alta);
    }

    @Test
    void alertaLeidaNoSeReabreSiLaPrioridadNoCambia() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 8);
        Alerta leida = alerta(PrioridadAlerta.ALTA, true, "La fecha de cierre era el 01/01/2020.");
        guardado(concurso);
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>(List.of(leida)));

        assertFalse(service.evaluarConcursoSinCerrar(concurso));

        assertTrue(leida.isLeida());
        assertTrue(leida.getMensaje().contains("hace 8 días"));
        verify(alertaRepository).save(leida);
    }

    @Test
    void elEscaladoActualizaLaMismaAlerta() {
        List<Alerta> alertas = new ArrayList<>();
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 4);
        guardado(concurso);
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(alertas);
        when(alertaRepository.save(any())).thenAnswer(invocacion -> {
            Alerta guardada = invocacion.getArgument(0);
            if (!alertas.contains(guardada)) {
                alertas.add(guardada);
            }
            return guardada;
        });

        assertTrue(service.evaluarConcursoSinCerrar(concurso));
        Alerta media = alertas.get(0);
        assertEquals(PrioridadAlerta.MEDIA, media.getPrioridad());
        media.setLeida(true);
        concurso.setFechaCierre(LocalDate.now().minusDays(8));

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        assertEquals(1, alertas.size());
        assertEquals(PrioridadAlerta.ALTA, media.getPrioridad());
        assertFalse(media.isLeida());
        assertTrue(media.getMensaje().contains("hace 8 días"));
    }

    @Test
    void elDescensoDePrioridadNoReabre() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 4);
        Alerta alta = alerta(PrioridadAlerta.ALTA, true, "mensaje previo");
        guardado(concurso);
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>(List.of(alta)));

        assertFalse(service.evaluarConcursoSinCerrar(concurso));

        assertEquals(PrioridadAlerta.MEDIA, alta.getPrioridad());
        assertTrue(alta.isLeida());
        verify(alertaRepository).save(alta);
    }

    @Test
    void laCausaQueYaNoCorrespondeQuedaLeida() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 2);
        Alerta alta = alerta(PrioridadAlerta.ALTA, false, "mensaje previo");
        guardado(concurso);
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>(List.of(alta)));

        assertFalse(service.evaluarConcursoSinCerrar(concurso));

        assertTrue(alta.isLeida());
        verify(alertaRepository).save(alta);
    }
}
