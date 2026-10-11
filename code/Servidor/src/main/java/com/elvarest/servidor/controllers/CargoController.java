package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.CargoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cargos")
public class CargoController extends BaseController<Cargo, Long> {

    private final CargoService cargoService;

    public CargoController(CargoService service) {
        super(service);
        this.cargoService = service;
    }

    /** Reglas de eliminación: validadas en el servidor antes de dar de baja el registro. */
    @Override
    protected void preDelete(Long id) throws ErrorServiceException {
        cargoService.validarEliminacion(id);
    }
}
