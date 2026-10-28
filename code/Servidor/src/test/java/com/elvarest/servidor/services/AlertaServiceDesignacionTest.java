package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
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
 * Alertas de fin de designación: 0 a 10 días -> ALTA, 11 a 15 -> MEDIA, solo designaciones
 * activas y con fecha de fin definitiva.
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceDesignacionTest {

    private static final Long DESIGNACION_ID = 1L;

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
    private Docente docente;

    @BeforeEach
    void setUp() {
        service = new AlertaService(alertaRepository, certificadoRepository, designacionRepository,
                concursoRepository, concursoDocenteRepository, cargoRepository);
        docente = new Docente();
        docente.setId(7L);
        docente.setNombre("Juan");
        docente.setApellido("Pérez");
        lenient().when(cargoRepository.findAll()).thenReturn(List.of());
    }

    private Designacion designacion(EstadoDesignacion estado, LocalDate fechaFin) {
        Designacion designacion = new Designacion();
        designacion.setId(DESIGNACION_ID);
        designacion.setDocente(docente);
        designacion.setEstadoDesignacion(estado);
        designacion.setFechaFinDefinitiva(fechaFin);
        designacion.setAlertas(new ArrayList<>());
        return designacion;
    }

    private void guardada(Designacion designacion) {
        when(designacionRepository.findById(DESIGNACION_ID)).thenReturn(Optional.of(designacion));
    }

    private Alerta alerta(PrioridadAlerta prioridad, boolean leida, String mensaje) {
        Alerta alerta = new Alerta();
        alerta.setId(50L);
        alerta.setPrioridad(prioridad);
        alerta.setLeida(leida);
        alerta.setMensaje(mensaje);
        return alerta;
    }

    @Test
    void diezDiasOmenosGeneraAlertaAlta() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(5));
        guardada(designacion);

        assertTrue(service.evaluarVencimientoDesignacion(designacion));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        Alerta creada = captor.getValue();
        assertEquals(PrioridadAlerta.ALTA, creada.getPrioridad());
        assertFalse(creada.isLeida());
        assertTrue(creada.getMensaje().contains("Pérez, Juan"));
        assertTrue(creada.getMensaje().contains("faltan 5 días"));
        assertTrue(designacion.getAlertas().contains(creada));
    }

    @Test
    void quinceDiasOmenosGeneraAlertaMedia() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(12));
        guardada(designacion);

        assertTrue(service.evaluarVencimientoDesignacion(designacion));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(PrioridadAlerta.MEDIA, captor.getValue().getPrioridad());
    }

    @Test
    void fechaFueraDeRangoNoGeneraAlerta() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(30));
        guardada(designacion);

        assertFalse(service.evaluarVencimientoDesignacion(designacion));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void designacionVencidaSigueActivaGeneraAlertaAlta() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().minusDays(2));
        guardada(designacion);

        assertTrue(service.evaluarVencimientoDesignacion(designacion));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(PrioridadAlerta.ALTA, captor.getValue().getPrioridad());
        assertTrue(captor.getValue().getMensaje().contains("finalizó el"));
    }

    @Test
    void designacionFinalizadaOsinFechaNoGeneraAlerta() {
        Designacion finalizada = designacion(EstadoDesignacion.FINALIZADO, LocalDate.now().plusDays(3));
        guardada(finalizada);
        assertFalse(service.evaluarVencimientoDesignacion(finalizada));

        Designacion sinFecha = designacion(EstadoDesignacion.ACTIVO, null);
        guardada(sinFecha);
        assertFalse(service.evaluarVencimientoDesignacion(sinFecha));

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void designacionEliminadaNoGeneraAlerta() {
        Designacion eliminada = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(3));
        eliminada.setEliminado(true);
        guardada(eliminada);

        assertFalse(service.evaluarVencimientoDesignacion(eliminada));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void alertaVigenteActualizaElMensajeSinReabrir() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(3));
        Alerta alta = alerta(PrioridadAlerta.ALTA, false, "mensaje previo");
        designacion.getAlertas().add(alta);
        guardada(designacion);

        assertFalse(service.evaluarVencimientoDesignacion(designacion));

        assertFalse(alta.isLeida());
        assertTrue(alta.getMensaje().contains("faltan 3 días"));
        verify(alertaRepository).save(alta);
    }

    @Test
    void alertaLeidaNoSeReabreSiLaPrioridadNoCambia() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(3));
        Alerta leida = alerta(PrioridadAlerta.ALTA, true, "La designación vencía el 01/01/2026.");
        designacion.getAlertas().add(leida);
        guardada(designacion);

        assertFalse(service.evaluarVencimientoDesignacion(designacion));

        assertTrue(leida.isLeida());
        assertTrue(leida.getMensaje().contains(LocalDate.now().plusDays(3).format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        verify(alertaRepository).save(leida);
        assertEquals(1, designacion.getAlertas().size());
    }

    @Test
    void elEscaladoActualizaLaMismaAlerta() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(12));
        guardada(designacion);

        assertTrue(service.evaluarVencimientoDesignacion(designacion));
        Alerta media = designacion.getAlertas().get(0);
        assertEquals(PrioridadAlerta.MEDIA, media.getPrioridad());
        media.setLeida(true);

        designacion.setFechaFinDefinitiva(LocalDate.now().plusDays(5));
        assertTrue(service.evaluarVencimientoDesignacion(designacion));

        assertEquals(1, designacion.getAlertas().size());
        assertEquals(PrioridadAlerta.ALTA, media.getPrioridad());
        assertFalse(media.isLeida());
        assertTrue(media.getMensaje().contains("faltan 5 días"));
    }

    @Test
    void elDescensoDePrioridadNoReabre() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(5));
        Alerta alta = alerta(PrioridadAlerta.ALTA, true, "mensaje previo");
        designacion.getAlertas().add(alta);
        guardada(designacion);

        designacion.setFechaFinDefinitiva(LocalDate.now().plusDays(12));

        assertFalse(service.evaluarVencimientoDesignacion(designacion));

        assertEquals(PrioridadAlerta.MEDIA, alta.getPrioridad());
        assertTrue(alta.isLeida());
        verify(alertaRepository).save(alta);
    }

    @Test
    void alertaDadaDeBajaNoSeTocaSiLaPrioridadNoCambia() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(3));
        Alerta baja = alerta(PrioridadAlerta.ALTA, true, "mensaje previo");
        baja.setEliminado(true);
        designacion.getAlertas().add(baja);
        guardada(designacion);

        assertFalse(service.evaluarVencimientoDesignacion(designacion));

        assertTrue(baja.isLeida());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void laBajaSeReactivaCuandoElAvisoEscala() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(3));
        Alerta baja = alerta(PrioridadAlerta.MEDIA, true, "mensaje previo");
        baja.setEliminado(true);
        designacion.getAlertas().add(baja);
        guardada(designacion);

        assertTrue(service.evaluarVencimientoDesignacion(designacion));

        assertEquals(PrioridadAlerta.ALTA, baja.getPrioridad());
        assertFalse(baja.isLeida());
        assertFalse(baja.getEliminado());
        verify(alertaRepository).save(baja);
    }

    @Test
    void laCausaQueYaNoCorrespondeQuedaLeida() {
        Designacion designacion = designacion(EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(30));
        Alerta media = alerta(PrioridadAlerta.MEDIA, false, "mensaje previo");
        designacion.getAlertas().add(media);
        guardada(designacion);

        assertFalse(service.evaluarVencimientoDesignacion(designacion));

        assertTrue(media.isLeida());
        verify(alertaRepository).save(media);
    }
}
