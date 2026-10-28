package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
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
 * Alerta de concurso cerrado con horas sin cubrir: se genera MEDIA cuando quedan horas que no
 * tienen designación ni se transfirieron a un rellamado, y se resuelve sola cuando se cubren,
 * se transfieren o el concurso deja de estar cerrado.
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceHorasTest {

    private static final Long CONCURSO_ID = 9L;

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
        lenient().when(designacionRepository.findAll()).thenReturn(List.of());
    }

    private Concurso concurso(EstadoConcurso estado, Integer total, Integer transferidas) {
        Concurso concurso = new Concurso();
        concurso.setId(CONCURSO_ID);
        concurso.setDescripcion("Matemática 1° año");
        concurso.setEstadoConcurso(estado);
        concurso.setHorasTotales(total);
        concurso.setHorasTransferidas(transferidas);
        concurso.setEliminado(false);
        return concurso;
    }

    private ConcursoDocente postulacion(Integer horas, EstadoPostulacion estado) {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setHorasElegidas(horas);
        postulacion.setEstadoPostulacion(estado);
        postulacion.setEliminado(false);
        return postulacion;
    }

    private Alerta alertaHoras(boolean leida) {
        Alerta alerta = new Alerta();
        alerta.setId(30L);
        alerta.setConcursoId(CONCURSO_ID);
        alerta.setTipo(AlertaService.TIPO_CONCURSO_HORAS_SIN_CUBRIR);
        alerta.setPrioridad(PrioridadAlerta.MEDIA);
        alerta.setLeida(leida);
        alerta.setMensaje("El concurso quedó cerrado con 4 horas sin cubrir.");
        return alerta;
    }

    @Test
    void cerradoConHorasLibresGeneraAlertaMedia() {
        Concurso concurso = concurso(EstadoConcurso.FINALIZADO, 10, 0);
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>());
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID))
                .thenReturn(List.of(postulacion(4, EstadoPostulacion.DESIGNACION_DEFINITIVA)));

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        Alerta creada = captor.getValue();
        assertEquals(PrioridadAlerta.MEDIA, creada.getPrioridad());
        assertEquals(AlertaService.TIPO_CONCURSO_HORAS_SIN_CUBRIR, creada.getTipo());
        assertEquals(CONCURSO_ID, creada.getConcursoId());
        assertTrue(creada.getMensaje().contains("6 horas sin cubrir"));
    }

    @Test
    void cerradoSinHorasLibresNoGeneraAlerta() {
        Concurso concurso = concurso(EstadoConcurso.FINALIZADO, 10, 0);
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>());
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID))
                .thenReturn(List.of(postulacion(10, EstadoPostulacion.DESIGNACION_DEFINITIVA)));

        assertFalse(service.evaluarConcursoSinCerrar(concurso));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void horasTransferidasARellamadoResuelvenLaAlerta() {
        Concurso concurso = concurso(EstadoConcurso.FINALIZADO, 10, 4);
        Alerta alerta = alertaHoras(false);
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(alertaRepository.findByConcursoId(CONCURSO_ID))
                .thenReturn(new ArrayList<>(List.of(alerta)));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID))
                .thenReturn(List.of(postulacion(6, EstadoPostulacion.DESIGNACION_DEFINITIVA)));

        assertFalse(service.evaluarConcursoSinCerrar(concurso));

        assertTrue(alerta.isLeida());
        verify(alertaRepository).save(alerta);
    }

    @Test
    void postulacionCanceladaNoDescuentaHoras() {
        Concurso concurso = concurso(EstadoConcurso.FINALIZADO, 10, 0);
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>());
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID))
                .thenReturn(List.of(
                        postulacion(10, EstadoPostulacion.CANCELADO_INEFECTIVO),
                        postulacion(3, EstadoPostulacion.DESIGNACION_DEFINITIVA)));

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertTrue(captor.getValue().getMensaje().contains("7 horas sin cubrir"));
    }

    @Test
    void concursoAbiertoNoGeneraAlertaDeHoras() {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO, 10, 0);
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(new ArrayList<>());

        assertFalse(service.evaluarConcursoSinCerrar(concurso));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void laAlertaDeHorasConviveConLaDeConcursoSinCerrar() {
        Concurso concurso = concurso(EstadoConcurso.FINALIZADO, 10, 0);
        Alerta proceso = new Alerta();
        proceso.setId(31L);
        proceso.setConcursoId(CONCURSO_ID);
        proceso.setTipo(AlertaService.TIPO_CONCURSO_SIN_CERRAR);
        proceso.setPrioridad(PrioridadAlerta.MEDIA);
        proceso.setLeida(true);
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(alertaRepository.findByConcursoId(CONCURSO_ID))
                .thenReturn(new ArrayList<>(List.of(proceso)));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID)).thenReturn(List.of());

        assertTrue(service.evaluarConcursoSinCerrar(concurso));

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(AlertaService.TIPO_CONCURSO_HORAS_SIN_CUBRIR, captor.getValue().getTipo());
    }

    @Test
    void laDepuracionResuelveLaAlertaCuandoLasHorasSeTranfieren() {
        Alerta alerta = alertaHoras(false);
        Concurso concurso = concurso(EstadoConcurso.FINALIZADO, 10, 10);
        when(alertaRepository.findAll()).thenReturn(List.of(alerta));
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID)).thenReturn(List.of());

        assertEquals(1, service.depurarAlertasObsoletas());
        assertTrue(alerta.isLeida());
    }
}
