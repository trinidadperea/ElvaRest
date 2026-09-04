package com.elvarest.cliente.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/concursos")
public class ConcursoController {

    @GetMapping
    public String listar() {
        return "concursos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "concursos/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "concursos/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "concursos/editar";
    }

}
