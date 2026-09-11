package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.services.CargoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cargos")
public class CargoController extends BaseController<Cargo, Long> {

    public CargoController(CargoService service) {
        super(service);
    }
}
