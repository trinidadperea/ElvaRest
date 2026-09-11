package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Curso;
import com.elvarest.servidor.services.CursoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cursos")
public class CursoController extends BaseController<Curso, Long> {

    public CursoController(CursoService service) {
        super(service);
    }
}
