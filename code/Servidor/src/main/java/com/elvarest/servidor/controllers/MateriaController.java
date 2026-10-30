package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.services.MateriaService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/materias")
public class MateriaController extends BaseController<Materia, Long> {

    public MateriaController(MateriaService service) {
        super(service);
    }
}
