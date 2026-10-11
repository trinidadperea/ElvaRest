package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.DocenteService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/docentes")
public class DocenteController extends BaseController<Docente, Long> {

    private final DocenteService docenteService;

    public DocenteController(DocenteService service) {
        super(service);
        this.docenteService = service;
    }

    /** Reglas de eliminación: validadas en el servidor antes de dar de baja el registro. */
    @Override
    protected void preDelete(Long id) throws ErrorServiceException {
        docenteService.validarEliminacion(id);
    }
}
