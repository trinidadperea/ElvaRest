package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CursoDTO;
import com.elvarest.cliente.services.CursoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/academica")
public class AcademicaController extends BaseController<CursoDTO, Long> {

    public AcademicaController(CursoService service) {
        super(service);
        initController(new CursoDTO(), "Lista académica", "Editar curso", "academica/");
    }
}
