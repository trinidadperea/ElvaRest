package com.elvarest.cliente.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    @GetMapping
    public String listar() {
        return "usuarios/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "usuarios/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "usuarios/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "usuarios/editar";
    }
}
