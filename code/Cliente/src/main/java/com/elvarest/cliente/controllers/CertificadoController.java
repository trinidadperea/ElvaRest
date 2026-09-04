package com.elvarest.cliente.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/certificados")
public class CertificadoController {

    @GetMapping
    public String listar() {
        return "certificados/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo() {
        return "certificados/nuevo";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id) {
        return "certificados/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id) {
        return "certificados/editar";
    }
}
