package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.services.ConcursoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concursos")
public class ConcursoController extends BaseController<Concurso, Long> {

    public ConcursoController(ConcursoService service) {
        super(service);
    }
}
