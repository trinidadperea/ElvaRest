package com.elvarest.servidor.scheduling;

import com.elvarest.servidor.services.AlertaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revisa las alertas automáticas: primero depura las que quedaron sin causa (las marca leídas)
 * y después actualiza o genera las de certificados por vencer, designaciones por terminar,
 * concursos que siguen abiertos después de su fecha de cierre y concursos cerrados con horas
 * sin cubrir.
 */
@Component
public class AlertasScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertasScheduler.class);

    private final AlertaService alertaService;

    public AlertasScheduler(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    /** Todos los días a las 6:00 am */
    @Scheduled(cron = "${alertas.cron:${alertas.certificados.cron:0 0 6 * * *}}",
            zone = "America/Argentina/Buenos_Aires")
    public void revisionDiaria() {
        revisar();
    }

    /** También al iniciar, por si el servidor estuvo apagado a la hora programada */
    @EventListener(ApplicationReadyEvent.class)
    public void revisionAlIniciar() {
        revisar();
    }

    private void revisar() {
        try {
            // La depuración va primero: las alertas sin causa no interfieren con la evaluación de las vigentes.
            int resueltas = alertaService.depurarAlertasObsoletas();
            int certificados = alertaService.generarAlertasDeVencimiento();
            int designaciones = alertaService.generarAlertasDeDesignaciones();
            int concursos = alertaService.generarAlertasDeConcursos();
            log.info("Revisión de alertas automáticas: {} resuelta(s), {} de certificados, "
                            + "{} de designaciones y {} de concursos.",
                    resueltas, certificados, designaciones, concursos);
        } catch (Exception e) {
            log.error("Falló la revisión de alertas automáticas", e);
        }
    }
}
