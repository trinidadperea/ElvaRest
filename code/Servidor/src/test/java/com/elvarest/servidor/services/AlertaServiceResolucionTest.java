package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.repositories.AlertaRepository;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Fecha de resolución de las alertas: la registra el Servidor cuando la alerta pasa a leída, la
 * conserva al editar una alerta ya resuelta y la limpia si vuelve a pendiente. El formulario del
 * Cliente no la envía, por eso no se toma de lo que llega.
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceResolucionTest {

    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private CertificadoRepository certificadoRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;
    @Mock
    private CargoRepository cargoRepository;

    private AlertaService service;

    @BeforeEach
    void setUp() {
        service = new AlertaService(alertaRepository, certificadoRepository, designacionRepository,
                concursoRepository, concursoDocenteRepository, cargoRepository);
        when(alertaRepository.save(any(Alerta.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void laAlertaQueNaceLeidaRegistraLaFechaDeHoy() throws Exception {
        Alerta alerta = alerta(true, null);

        service.alta(alerta);

        assertEquals(LocalDate.now(), alerta.getFechaResolucion());
    }

    @Test
    void laAlertaQueNacePendienteNoRegistraFecha() throws Exception {
        Alerta alerta = alerta(false, null);

        service.alta(alerta);

        assertNull(alerta.getFechaResolucion());
    }

    @Test
    void marcarLeidaRegistraLaFechaDeHoy() throws Exception {
        Alerta guardada = alerta(false, null);
        guardada.setId(1L);
        Alerta nueva = alerta(true, null);
        when(alertaRepository.findById(1L)).thenReturn(Optional.of(guardada));

        service.modificar(1L, nueva);

        assertEquals(LocalDate.now(), nueva.getFechaResolucion());
    }

    @Test
    void editarUnaAlertaResueltaConservaLaFechaOriginal() throws Exception {
        LocalDate original = LocalDate.of(2026, 10, 20);
        Alerta guardada = alerta(true, original);
        guardada.setId(2L);
        Alerta nueva = alerta(true, null);
        when(alertaRepository.findById(2L)).thenReturn(Optional.of(guardada));

        service.modificar(2L, nueva);

        assertEquals(original, nueva.getFechaResolucion());
    }

    @Test
    void reabrirLaAlertaLimpiaLaFechaDeResolucion() throws Exception {
        Alerta guardada = alerta(true, LocalDate.of(2026, 10, 20));
        guardada.setId(3L);
        Alerta nueva = alerta(false, LocalDate.of(2026, 10, 20));
        when(alertaRepository.findById(3L)).thenReturn(Optional.of(guardada));

        service.modificar(3L, nueva);

        assertNull(nueva.getFechaResolucion());
    }

    private Alerta alerta(boolean leida, LocalDate fechaResolucion) {
        Alerta alerta = new Alerta();
        alerta.setMensaje("Aviso manual");
        alerta.setFechaEmision(LocalDate.now());
        alerta.setLeida(leida);
        alerta.setFechaResolucion(fechaResolucion);
        return alerta;
    }
}
