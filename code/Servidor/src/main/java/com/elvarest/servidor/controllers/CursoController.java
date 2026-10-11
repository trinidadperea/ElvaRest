package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Curso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.CursoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cursos")
public class CursoController extends BaseController<Curso, Long> {

    private final CursoService cursoService;

    public CursoController(CursoService service) {
        super(service);
        this.cursoService = service;
    }

    /** Reglas de eliminación: validadas en el servidor antes de dar de baja el registro. */
    @Override
    protected void preDelete(Long id) throws ErrorServiceException {
        cursoService.validarEliminacion(id);
    }
}
