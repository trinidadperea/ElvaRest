package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Licencia;
import com.elvarest.servidor.services.LicenciaService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/licencias")
public class LicenciaController extends BaseController<Licencia, Long> {

    public LicenciaController(LicenciaService service) {
        super(service);
    }
}
