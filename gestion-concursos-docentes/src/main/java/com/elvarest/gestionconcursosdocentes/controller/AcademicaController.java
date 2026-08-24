package com.elvarest.gestionconcursosdocentes.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/academica")
public class AcademicaController {

    @GetMapping
    public String listar() {
        return "academica/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "academica/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "academica/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "academica/editar";
    }
}
