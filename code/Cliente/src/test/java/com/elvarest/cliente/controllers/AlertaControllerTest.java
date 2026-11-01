package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.AlertaDTO;
import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.services.AlertaService;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * El detalle de la alerta solo ofrece «Ver concurso» / «Ver certificado» si el destino sigue
 * existiendo: cuando la causa se elimina, la alerta queda leída pero sin un enlace que termine
 * en una pantalla de «no encontrado».
 */
@ExtendWith(MockitoExtension.class)
class AlertaControllerTest {

    @Mock
    private AlertaService alertaService;
    @Mock
    private CertificadoService certificadoService;
    @Mock
    private ConcursoService concursoService;

    private AlertaController controller;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        controller = new AlertaController(alertaService, certificadoService, concursoService);
        model = new ExtendedModelMap();
        controller.model = model;
    }

    @Test
    void elConcursoEliminadoNoOfreceElEnlace() {
        AlertaDTO alerta = alertaDeConcurso("alert-1", "conc-9");
        when(concursoService.obtener("conc-9")).thenThrow(new RuntimeException("Entidad no encontrada o eliminada"));

        controller.postConsulta(alerta);

        assertFalse(model.containsAttribute("concursoDestinoUuid"));
    }

    @Test
    void elConcursoVigenteOfreceElEnlace() {
        AlertaDTO alerta = alertaDeConcurso("alert-1", "conc-9");
        when(concursoService.obtener("conc-9")).thenReturn(new ConcursoDTO());

        controller.postConsulta(alerta);

        assertEquals("conc-9", model.getAttribute("concursoDestinoUuid"));
    }

    @Test
    void elCertificadoEliminadoNoOfreceElEnlace() {
        AlertaDTO alerta = new AlertaDTO();
        alerta.setId("alert-2");
        CertificadoDTO certificado = new CertificadoDTO();
        certificado.setId("cert-4");
        alerta.setCertificado(certificado);
        when(certificadoService.obtener("cert-4")).thenThrow(new RuntimeException("Entidad no encontrada o eliminada"));

        controller.postConsulta(alerta);

        assertFalse(model.containsAttribute("certificadoDestinoUuid"));
    }

    private AlertaDTO alertaDeConcurso(String id, String concursoUuid) {
        AlertaDTO alerta = new AlertaDTO();
        alerta.setId(id);
        alerta.setConcursoUuid(concursoUuid);
        return alerta;
    }
}
