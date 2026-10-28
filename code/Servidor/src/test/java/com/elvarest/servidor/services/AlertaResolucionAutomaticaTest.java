package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.CaracterCertificado;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
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
 * Resolución automática de las alertas automáticas: cuando la causa desaparece la alerta se marca
 * leída con fecha de resolución, sin borrarse. Se cubren las cuatro causas (certificado,
 * designación, concurso sin cerrar y horas sin cubrir) y se controla que lo que sigue vigente, lo
 * manual y lo dado de baja no se toquen.
 */
@ExtendWith(MockitoExtension.class)
class AlertaResolucionAutomaticaTest {

    private static final Long CONCURSO_ID = 9L;
    private static final Long CONCURSO_HORAS_ID = 10L;

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
    }

    // ------------------------------------------------------------------
    // Certificados
    // ------------------------------------------------------------------

    @Test
    void elCertificadoReemplazadoResuelveSuAlerta() {
        Certificado reemplazado = certificado(1L, CaracterCertificado.PROVISORIO, false, null);
        Alerta alerta = alerta(11L);

        when(alertaRepository.findByCertificadoId(1L)).thenReturn(List.of(alerta));

        assertFalse(service.evaluarVencimiento(reemplazado));
        assertResuelta(alerta);
    }

    @Test
    void elCertificadoDadoDeBajaResuelveSuAlerta() {
        Certificado baja = certificado(2L, CaracterCertificado.PROVISORIO, true, LocalDate.now().plusDays(3));
        baja.setEliminado(true);
        Alerta alerta = alerta(12L);

        when(alertaRepository.findByCertificadoId(2L)).thenReturn(List.of(alerta));

        assertFalse(service.evaluarVencimiento(baja));
        assertResuelta(alerta);
    }

    @Test
    void elCertificadoDefinitivoOFueraDeUmbralResuelveSuAlerta() {
        Certificado definitivo = certificado(3L, CaracterCertificado.DEFINITIVO, true, null);
        Certificado lejano = certificado(4L, CaracterCertificado.PROVISORIO, true, LocalDate.now().plusDays(30));
        Alerta porDefinitivo = alerta(13L);
        Alerta porLejano = alerta(14L);

        when(alertaRepository.findByCertificadoId(3L)).thenReturn(List.of(porDefinitivo));
        when(alertaRepository.findByCertificadoId(4L)).thenReturn(List.of(porLejano));

        assertFalse(service.evaluarVencimiento(definitivo));
        assertFalse(service.evaluarVencimiento(lejano));
        assertResuelta(porDefinitivo);
        assertResuelta(porLejano);
    }

    @Test
    void elCertificadoDeUnaPostulacionCanceladaResuelveSuAlerta() {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(20L);
        postulacion.setEstadoPostulacion(EstadoPostulacion.CANCELADO_INEFECTIVO);
        Certificado cancelado = certificado(5L, CaracterCertificado.PROVISORIO, true, LocalDate.now().plusDays(2));
        cancelado.setConcursoDocente(postulacion);
        Alerta alerta = alerta(15L);

        when(alertaRepository.findByCertificadoId(5L)).thenReturn(List.of(alerta));

        assertFalse(service.evaluarVencimiento(cancelado));
        assertResuelta(alerta);
    }

    @Test
    void unaAlertaDadaDeBajaNoSeResuelveSola() {
        Certificado reemplazado = certificado(6L, CaracterCertificado.PROVISORIO, false, null);
        Alerta baja = alerta(16L);
        baja.setEliminado(true);

        when(alertaRepository.findByCertificadoId(6L)).thenReturn(List.of(baja));

        service.evaluarVencimiento(reemplazado);

        assertFalse(baja.isLeida());
        verify(alertaRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Designaciones
    // ------------------------------------------------------------------

    @Test
    void laDesignacionFinalizadaResuelveSuAlerta() {
        Designacion designacion = designacion(30L, EstadoDesignacion.FINALIZADO, LocalDate.now().minusDays(10));
        Alerta alerta = alerta(17L);
        designacion.getAlertas().add(alerta);
        when(designacionRepository.findById(30L)).thenReturn(Optional.of(designacion));

        assertFalse(service.evaluarVencimientoDesignacion(designacion));
        assertResuelta(alerta);
    }

    @Test
    void laDesignacionEliminadaResuelveSuAlerta() {
        Designacion designacion = designacion(31L, EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(4));
        designacion.setEliminado(true);
        Alerta alerta = alerta(18L);
        designacion.getAlertas().add(alerta);
        when(designacionRepository.findById(31L)).thenReturn(Optional.of(designacion));

        assertFalse(service.evaluarVencimientoDesignacion(designacion));
        assertResuelta(alerta);
    }

    // ------------------------------------------------------------------
    // Concursos
    // ------------------------------------------------------------------

    @Test
    void elConcursoCerradoResuelveLaAlertaDeSigueEnProceso() {
        Concurso cerrado = concurso(CONCURSO_ID, EstadoConcurso.FINALIZADO, 12);
        cerrado.setHorasTotales(0);
        Alerta alerta = alertaDeConcurso(19L, CONCURSO_ID, AlertaService.TIPO_CONCURSO_SIN_CERRAR);

        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(cerrado));
        when(alertaRepository.findByConcursoId(CONCURSO_ID)).thenReturn(List.of(alerta));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_ID)).thenReturn(List.of());

        assertFalse(service.evaluarConcursoSinCerrar(cerrado));
        assertResuelta(alerta);
    }

    @Test
    void lasHorasCubiertasResuelvenLaAlertaDeHorasSinCubrir() {
        Concurso cerrado = concurso(CONCURSO_HORAS_ID, EstadoConcurso.FINALIZADO, 12);
        cerrado.setHorasTotales(24);
        Alerta alerta = alertaDeConcurso(20L, CONCURSO_HORAS_ID, AlertaService.TIPO_CONCURSO_HORAS_SIN_CUBRIR);

        when(concursoRepository.findById(CONCURSO_HORAS_ID)).thenReturn(Optional.of(cerrado));
        when(alertaRepository.findByConcursoId(CONCURSO_HORAS_ID)).thenReturn(List.of(alerta));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_HORAS_ID))
                .thenReturn(List.of(postulacion(24)));

        assertFalse(service.evaluarConcursoSinCerrar(cerrado));
        assertResuelta(alerta);
    }

    // ------------------------------------------------------------------
    // Depuración diaria: causas que solo se resuelven por baja o cancelación
    // ------------------------------------------------------------------

    @Test
    void laDepuracionResuelveLasCausasDesaparecidasYRespetaElResto() {
        // Certificado dado de baja.
        Certificado certificadoBaja = certificado(7L, CaracterCertificado.PROVISORIO, true, LocalDate.now().plusDays(3));
        certificadoBaja.setEliminado(true);
        Alerta porCertificado = alerta(21L);
        porCertificado.setCertificado(certificadoBaja);

        // Designación eliminada (el vínculo vive en la colección de la designación).
        Designacion designacion = designacion(32L, EstadoDesignacion.ACTIVO, LocalDate.now().plusDays(4));
        designacion.setEliminado(true);
        Alerta porDesignacion = alerta(22L);
        designacion.getAlertas().add(porDesignacion);

        // Concurso eliminado y concurso con las horas cubiertas.
        Concurso eliminado = concurso(CONCURSO_ID, EstadoConcurso.EN_PROCESO, 10);
        eliminado.setEliminado(true);
        Alerta porConcurso = alertaDeConcurso(23L, CONCURSO_ID, AlertaService.TIPO_CONCURSO_SIN_CERRAR);
        Concurso conHorasCubiertas = concurso(CONCURSO_HORAS_ID, EstadoConcurso.FINALIZADO, 10);
        conHorasCubiertas.setHorasTotales(10);
        conHorasCubiertas.setHorasTransferidas(10);
        Alerta porHoras = alertaDeConcurso(24L, CONCURSO_HORAS_ID, AlertaService.TIPO_CONCURSO_HORAS_SIN_CUBRIR);

        // Alerta manual y alerta dada de baja: no se tocan.
        Alerta manual = alerta(25L);
        Alerta baja = alerta(26L);
        baja.setEliminado(true);

        when(alertaRepository.findAll())
                .thenReturn(List.of(porCertificado, porDesignacion, porConcurso, porHoras, manual, baja));
        when(designacionRepository.findAll()).thenReturn(List.of(designacion));
        when(concursoRepository.findById(CONCURSO_ID)).thenReturn(Optional.of(eliminado));
        when(concursoRepository.findById(CONCURSO_HORAS_ID)).thenReturn(Optional.of(conHorasCubiertas));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(CONCURSO_HORAS_ID)).thenReturn(List.of());

        assertEquals(4, service.depurarAlertasObsoletas());

        assertResuelta(porCertificado);
        assertResuelta(porDesignacion);
        assertResuelta(porConcurso);
        assertResuelta(porHoras);
        assertFalse(manual.isLeida());
        assertFalse(baja.isLeida());
        verify(alertaRepository, never()).save(manual);
        verify(alertaRepository, never()).save(baja);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Certificado certificado(Long id, CaracterCertificado caracter, boolean actual, LocalDate vencimiento) {
        Certificado certificado = new Certificado();
        certificado.setId(id);
        certificado.setCaracter(caracter);
        certificado.setActual(actual);
        certificado.setFechaVencimiento(vencimiento);
        certificado.setEliminado(false);
        return certificado;
    }

    private Designacion designacion(Long id, EstadoDesignacion estado, LocalDate fechaFin) {
        Designacion designacion = new Designacion();
        designacion.setId(id);
        designacion.setEstadoDesignacion(estado);
        designacion.setFechaFinDefinitiva(fechaFin);
        designacion.setEliminado(false);
        designacion.setAlertas(new ArrayList<>());
        return designacion;
    }

    private Concurso concurso(Long id, EstadoConcurso estado, int diasDesdeElCierre) {
        Concurso concurso = new Concurso();
        concurso.setId(id);
        concurso.setDescripcion("Concurso " + id);
        concurso.setEstadoConcurso(estado);
        concurso.setFechaCierre(LocalDate.now().minusDays(diasDesdeElCierre));
        concurso.setEliminado(false);
        return concurso;
    }

    private ConcursoDocente postulacion(int horas) {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setHorasElegidas(horas);
        postulacion.setEstadoPostulacion(EstadoPostulacion.DESIGNACION_DEFINITIVA);
        postulacion.setEliminado(false);
        return postulacion;
    }

    private Alerta alerta(Long id) {
        Alerta alerta = new Alerta();
        alerta.setId(id);
        alerta.setPrioridad(PrioridadAlerta.MEDIA);
        alerta.setLeida(false);
        // El mensaje distingue las alertas: el equals no incluye el id heredado.
        alerta.setMensaje("mensaje previo " + id);
        return alerta;
    }

    private Alerta alertaDeConcurso(Long id, Long concursoId, String tipo) {
        Alerta alerta = alerta(id);
        alerta.setConcursoId(concursoId);
        alerta.setTipo(tipo);
        return alerta;
    }

    private void assertResuelta(Alerta alerta) {
        assertTrue(alerta.isLeida(), "La alerta debe quedar leída.");
        assertEquals(LocalDate.now(), alerta.getFechaResolucion(), "Debe registrar la fecha de resolución.");
        verify(alertaRepository).save(alerta);
    }
}
