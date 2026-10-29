package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.CaracterCertificado;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Depuración diaria: las alertas automáticas cuya causa desapareció se marcan leídas (nunca se
 * eliminan) y las manuales no se tocan.
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceDepuracionTest {

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
    }

    private Alerta alerta(Long id, PrioridadAlerta prioridad) {
        Alerta alerta = new Alerta();
        alerta.setId(id);
        alerta.setPrioridad(prioridad);
        alerta.setLeida(false);
        alerta.setMensaje("mensaje");
        return alerta;
    }

    @Test
    void alertaDeConcursoFinalizadoQuedaLeida() {
        Alerta alerta = alerta(1L, PrioridadAlerta.MEDIA);
        alerta.setConcursoId(9L);
        Concurso concurso = new Concurso();
        concurso.setId(9L);
        concurso.setEstadoConcurso(EstadoConcurso.FINALIZADO);
        concurso.setFechaCierre(LocalDate.now().minusDays(5));

        when(alertaRepository.findAll()).thenReturn(List.of(alerta));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoRepository.findById(9L)).thenReturn(Optional.of(concurso));

        assertEquals(1, service.depurarAlertasObsoletas());
        assertTrue(alerta.isLeida());
        verify(alertaRepository).save(alerta);
    }

    @Test
    void alertaDeCertificadoRenovadoQuedaLeida() {
        Alerta alerta = alerta(2L, PrioridadAlerta.MEDIA);
        Certificado certificado = new Certificado();
        certificado.setCaracter(CaracterCertificado.PROVISORIO);
        certificado.setActual(false);
        certificado.setFechaVencimiento(LocalDate.now().plusDays(5));
        alerta.setCertificado(certificado);

        when(alertaRepository.findAll()).thenReturn(List.of(alerta));
        when(designacionRepository.findAll()).thenReturn(List.of());

        assertEquals(1, service.depurarAlertasObsoletas());
        assertTrue(alerta.isLeida());
    }

    @Test
    void alertaDeDesignacionFinalizadaQuedaLeida() {
        Alerta alerta = alerta(3L, PrioridadAlerta.ALTA);
        Designacion designacion = new Designacion();
        designacion.setId(4L);
        designacion.setEstadoDesignacion(EstadoDesignacion.FINALIZADO);
        designacion.setFechaFinDefinitiva(LocalDate.now().minusDays(1));
        designacion.setAlertas(new ArrayList<>(List.of(alerta)));

        when(alertaRepository.findAll()).thenReturn(List.of(alerta));
        when(designacionRepository.findAll()).thenReturn(List.of(designacion));

        assertEquals(1, service.depurarAlertasObsoletas());
        assertTrue(alerta.isLeida());
    }

    @Test
    void alertaQueSigueVigenteNoSeToca() {
        Alerta alerta = alerta(5L, PrioridadAlerta.MEDIA);
        alerta.setConcursoId(9L);
        Concurso concurso = new Concurso();
        concurso.setId(9L);
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        concurso.setFechaCierre(LocalDate.now().minusDays(4));

        when(alertaRepository.findAll()).thenReturn(List.of(alerta));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoRepository.findById(9L)).thenReturn(Optional.of(concurso));

        assertEquals(0, service.depurarAlertasObsoletas());
        assertFalse(alerta.isLeida());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void alertaDeConcursoQueEscaloNoSeResuelveEnLaDepuracion() {
        Alerta alerta = alerta(7L, PrioridadAlerta.MEDIA);
        alerta.setConcursoId(9L);
        Concurso concurso = new Concurso();
        concurso.setId(9L);
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        concurso.setFechaCierre(LocalDate.now().minusDays(8));

        when(alertaRepository.findAll()).thenReturn(List.of(alerta));
        when(designacionRepository.findAll()).thenReturn(List.of());
        when(concursoRepository.findById(9L)).thenReturn(Optional.of(concurso));

        assertEquals(0, service.depurarAlertasObsoletas());
        assertFalse(alerta.isLeida());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void alertaManualNoSeToca() {
        Alerta manual = alerta(6L, PrioridadAlerta.ALTA);
        when(alertaRepository.findAll()).thenReturn(List.of(manual));
        when(designacionRepository.findAll()).thenReturn(List.of());

        assertEquals(0, service.depurarAlertasObsoletas());
        assertFalse(manual.isLeida());
        verify(alertaRepository, never()).save(any());
    }
}
