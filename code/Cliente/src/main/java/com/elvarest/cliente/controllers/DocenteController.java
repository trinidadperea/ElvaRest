package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.services.DocenteService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/docentes")
public class DocenteController extends BaseController<DocenteDTO, Long> {

    public DocenteController(DocenteService service) {
        super(service);
        initController(new DocenteDTO(), "Lista de docentes", "Editar docente", "docentes/");
    }
}
