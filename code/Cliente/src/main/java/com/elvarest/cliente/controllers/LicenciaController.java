package com.elvarest.cliente.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/licencias")
public class LicenciaController {

    @GetMapping
    public String listar() {
        return "licencias/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "licencias/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "licencias/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "licencias/editar";
    }
}
