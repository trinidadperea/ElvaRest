package com.elvarest.gestionconcursosdocentes.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/designaciones")
public class DesignacionController {

    @GetMapping
    public String listar() {
        return "designaciones/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "designaciones/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "designaciones/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "designaciones/editar";
    }
}
