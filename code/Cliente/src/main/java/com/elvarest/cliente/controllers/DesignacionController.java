package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.services.DesignacionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/designaciones")
public class DesignacionController extends BaseController<DesignacionDTO, Long> {

    public DesignacionController(DesignacionService service) {
        super(service);
        initController(new DesignacionDTO(), "Lista de designaciones", "Editar designación", "designaciones/");
    }
}
