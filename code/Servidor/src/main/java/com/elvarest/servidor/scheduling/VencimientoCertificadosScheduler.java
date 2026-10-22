package com.elvarest.servidor.scheduling;

import com.elvarest.servidor.services.AlertaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara la revisión de vencimientos de certificados provisorios
 */
@Component
public class VencimientoCertificadosScheduler {

    private static final Logger log = LoggerFactory.getLogger(VencimientoCertificadosScheduler.class);

    private final AlertaService alertaService;

    public VencimientoCertificadosScheduler(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    /** Todos los días a las 6:00 am */
    @Scheduled(cron = "${alertas.certificados.cron:0 0 6 * * *}", zone = "America/Argentina/Buenos_Aires")
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
            int creadas = alertaService.generarAlertasDeVencimiento();
            log.info("Revisión de vencimientos de certificados: {} alerta(s) nueva(s).", creadas);
        } catch (Exception e) {
            log.error("Falló la revisión de vencimientos de certificados", e);
        }
    }
}