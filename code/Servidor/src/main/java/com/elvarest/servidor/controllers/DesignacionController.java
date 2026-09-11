package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.services.DesignacionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/designaciones")
public class DesignacionController extends BaseController<Designacion, Long> {

    public DesignacionController(DesignacionService service) {
        super(service);
    }
}
