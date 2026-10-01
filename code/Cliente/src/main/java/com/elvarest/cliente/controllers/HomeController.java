package com.elvarest.cliente.controllers;

import com.elvarest.cliente.services.AlertaService;
import com.elvarest.cliente.services.CargoService;
import com.elvarest.cliente.services.ConcursoService;
import com.elvarest.cliente.services.DocenteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DocenteService docenteService;
    private final AlertaService alertaService;
    private final ConcursoService concursoService;
    private final CargoService cargoService;

    public HomeController(DocenteService docenteService, AlertaService alertaService,
                          ConcursoService concursoService, CargoService cargoService) {
        this.docenteService = docenteService;
        this.alertaService = alertaService;
        this.concursoService = concursoService;
        this.cargoService = cargoService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("cantidadDocentes", docenteService.listarActivos().size());
        model.addAttribute("cantidadAlertas", alertaService.listarActivos().size());
        model.addAttribute("cantidadConcursos", concursoService.listarActivos().size());
        model.addAttribute("cantidadCargos", cargoService.listarActivos().size());
        return "dashboard/index";
    }
}
