package com.elvarest.cliente.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/alertas")
public class AlertaController {

    @GetMapping
    public String listar() {
        return "alertas/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "alertas/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "alertas/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "alertas/editar";
    }
}
