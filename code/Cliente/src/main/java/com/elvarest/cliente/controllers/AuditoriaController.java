package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.AuditoriaDTO;
import com.elvarest.cliente.services.AuditoriaDetalleParser;
import com.elvarest.cliente.services.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Historial de cambios del sistema: SOLO LECTURA y exclusivo del ADMIN (ver SecurityConfig).
 * A propósito no extiende BaseController, porque este expone rutas de alta, edición y baja
 * (incluida una baja por GET). Acá solo existen dos GET: el listado y el detalle por uuid.
 * No hay ningún método de escritura ni formularios que enlacen un AuditoriaDTO.
 */
@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private static final String VIEW_LIST = "auditoria/lista";
    private static final String VIEW_DETAIL = "auditoria/detalle";
    private static final String REDIRECT_LIST = "redirect:/auditoria";

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping({"", "/list"})
    public String listar(Model model) {
        model.addAttribute("titleList", "Historial de cambios");
        try {
            model.addAttribute("items", service.listar());
        } catch (Exception e) {
            log.error("No se pudo recuperar el historial de cambios", e);
            model.addAttribute("items", List.of());
            model.addAttribute("msgError", "No se pudo recuperar el historial de cambios. Intente nuevamente.");
        }
        return VIEW_LIST;
    }

    @GetMapping("/{uuid}")
    public String detalle(@PathVariable String uuid, Model model, RedirectAttributes attributes) {
        try {
            AuditoriaDTO encontrado = service.obtener(uuid);
            if (encontrado == null) {
                throw new IllegalArgumentException("No encontrado: " + uuid);
            }
            model.addAttribute("item", encontrado);
            model.addAttribute("vista", AuditoriaDetalleParser.interpretar(encontrado.getDetalle()));
            return VIEW_DETAIL;
        } catch (HttpClientErrorException.NotFound e) {
            attributes.addFlashAttribute("msgError", "El registro de auditoría solicitado no existe.");
        } catch (Exception e) {
            log.error("No se pudo recuperar el registro de auditoría {}", uuid, e);
            attributes.addFlashAttribute("msgError", "No se pudo recuperar el registro de auditoría.");
        }
        return REDIRECT_LIST;
    }
}