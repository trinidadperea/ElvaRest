package com.elvarest.cliente.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/docentes")
public class DocenteController {

    @GetMapping
    public String listar() {
        return "docentes/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "docentes/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "docentes/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "docentes/editar";
    }
}
