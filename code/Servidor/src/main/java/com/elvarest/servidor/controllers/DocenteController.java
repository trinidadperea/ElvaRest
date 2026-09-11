package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.services.DocenteService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/docentes")
public class DocenteController extends BaseController<Docente, Long> {

    public DocenteController(DocenteService service) {
        super(service);
    }
}
