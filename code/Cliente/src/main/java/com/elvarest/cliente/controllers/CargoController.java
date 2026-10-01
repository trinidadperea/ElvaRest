package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CargoDTO;
import com.elvarest.cliente.services.CargoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cargos")
public class CargoController extends BaseController<CargoDTO, Long> {

    public CargoController(CargoService service) {
        super(service);
        initController(new CargoDTO(), "Gestión de cargos", "Editar cargo", "cargos/");
    }
}
