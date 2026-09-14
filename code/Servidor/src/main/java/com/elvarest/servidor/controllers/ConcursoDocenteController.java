package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.services.ConcursoDocenteService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concursos-docentes")
public class ConcursoDocenteController extends BaseController<ConcursoDocente, Long> {

    public ConcursoDocenteController(ConcursoDocenteService service) {
        super(service);
    }
}
