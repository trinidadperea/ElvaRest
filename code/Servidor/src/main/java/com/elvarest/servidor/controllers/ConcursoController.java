package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.ConcursoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concursos")
public class ConcursoController extends BaseController<Concurso, Long> {

    private final ConcursoService concursoService;

    public ConcursoController(ConcursoService service) {
        super(service);
        this.concursoService = service;
    }

    /** Reglas de eliminación: validadas en el servidor antes de dar de baja el registro. */
    @Override
    protected void preDelete(Long id) throws ErrorServiceException {
        concursoService.validarEliminacion(id);
    }
}
