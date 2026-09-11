package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.services.AlertaService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alertas")
public class AlertaController extends BaseController<Alerta, Long> {

    public AlertaController(AlertaService service) {
        super(service);
    }
}
