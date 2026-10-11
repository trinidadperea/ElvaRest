package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.MateriaService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/materias")
public class MateriaController extends BaseController<Materia, Long> {

    private final MateriaService materiaService;

    public MateriaController(MateriaService service) {
        super(service);
        this.materiaService = service;
    }

    /** Reglas de eliminación: validadas en el servidor antes de dar de baja el registro. */
    @Override
    protected void preDelete(Long id) throws ErrorServiceException {
        materiaService.validarEliminacion(id);
    }
}
