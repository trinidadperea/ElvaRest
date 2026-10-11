package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.AlertaDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.AlertaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/alertas")
public class AlertaController extends BaseController<AlertaDTO, Long> {

    public AlertaController(AlertaService service) {
        super(service);
        initController(new AlertaDTO(), "Lista de alertas", "Editar alerta", "alertas/");
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
