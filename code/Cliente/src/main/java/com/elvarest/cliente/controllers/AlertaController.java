package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.AlertaDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.AlertaService;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Alertas del sistema. El listado y el detalle son genéricos; acá se resuelven las acciones
 * propias y el contexto de navegación hacia el certificado o el concurso de origen.
 */
@Controller
@RequestMapping("/alertas")
public class AlertaController extends BaseController<AlertaDTO, Long> {

    private final CertificadoService certificadoService;
    private final ConcursoService concursoService;

    public AlertaController(AlertaService service, CertificadoService certificadoService,
                            ConcursoService concursoService) {
        super(service);
        this.certificadoService = certificadoService;
        this.concursoService = concursoService;
        initController(new AlertaDTO(), "Lista de alertas", "Editar alerta", "alertas/");
    }

    /**
     * Publica el destino de «Ver certificado» / «Ver concurso» solo si la entidad asociada sigue
     * existiendo: la alerta puede sobrevivir a la baja del certificado o del concurso, y no
     * conviene ofrecer un enlace que termine en una pantalla de «no encontrado».
     */
    @Override
    protected void postConsulta(AlertaDTO found) {
        if (model == null) {
            return;
        }
        if (existeCertificado(found)) {
            model.addAttribute("certificadoDestinoId", found.getCertificado().getId());
        }
        if (existeConcurso(found)) {
            model.addAttribute("concursoDestinoId", found.getConcursoId());
        }
    }

    private boolean existeCertificado(AlertaDTO alerta) {
        if (alerta.getCertificado() == null || alerta.getCertificado().getId() == null) {
            return false;
        }
        try {
            return certificadoService.obtener(alerta.getCertificado().getId()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean existeConcurso(AlertaDTO alerta) {
        if (alerta.getConcursoId() == null) {
            return false;
        }
        try {
            return concursoService.obtener(alerta.getConcursoId()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Marca la alerta como leída / resuelta conservando el resto de sus datos. */
    @PostMapping("/{id}/marcar-leida")
    public String marcarLeida(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            AlertaDTO alerta = service.obtener(id);
            if (alerta == null) {
                throw new IllegalArgumentException("No encontrado: " + id);
            }
            alerta.setLeida(true);
            service.modificar(id, alerta);
            attributes.addFlashAttribute("msgExito", "La alerta quedó marcada como leída.");
        } catch (ErrorServiceException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        } catch (Exception e) {
            attributes.addFlashAttribute("msgError", "Error de Sistema");
        }
        return "redirect:/alertas/" + id;
    }
}
