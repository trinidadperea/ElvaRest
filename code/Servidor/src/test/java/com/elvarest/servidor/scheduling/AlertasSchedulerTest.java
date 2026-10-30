package com.elvarest.servidor.scheduling;

import com.elvarest.servidor.services.AlertaService;
import com.elvarest.servidor.services.DesignacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La revisión diaria finaliza primero las designaciones vencidas: así la depuración encuentra su
 * alerta sin causa y la marca leída en la misma corrida.
 */
@ExtendWith(MockitoExtension.class)
class AlertasSchedulerTest {

    @Mock
    private AlertaService alertaService;

    @Mock
    private DesignacionService designacionService;

    @Test
    void laRevisionDiariaFinalizaLasDesignacionesVencidasAntesDeDepurar() {
        AlertasScheduler scheduler = new AlertasScheduler(alertaService, designacionService);

        scheduler.revisionDiaria();

        InOrder orden = inOrder(designacionService, alertaService);
        orden.verify(designacionService).finalizarVencidas();
        orden.verify(alertaService).depurarAlertasObsoletas();
        orden.verify(alertaService).generarAlertasDeVencimiento();
        orden.verify(alertaService).generarAlertasDeDesignaciones();
        orden.verify(alertaService).generarAlertasDeConcursos();
    }

    @Test
    void alIniciarElServidorSeEjecutaLaMismaRevision() {
        AlertasScheduler scheduler = new AlertasScheduler(alertaService, designacionService);

        scheduler.revisionAlIniciar();

        verify(designacionService).finalizarVencidas();
        verify(alertaService).depurarAlertasObsoletas();
    }

    @Test
    void siLaFinalizacionFallaLaRevisionNoPropagaElError() {
        when(designacionService.finalizarVencidas()).thenThrow(new IllegalStateException("base caída"));
        AlertasScheduler scheduler = new AlertasScheduler(alertaService, designacionService);

        assertDoesNotThrow(scheduler::revisionDiaria);
    }
}
