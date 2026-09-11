package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.services.ConcursoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/concursos")
public class ConcursoController extends BaseController<ConcursoDTO, Long> {

    public ConcursoController(ConcursoService service) {
        super(service);
        initController(new ConcursoDTO(), "Lista de concursos", "Editar concurso", "concursos/");
    }
}
