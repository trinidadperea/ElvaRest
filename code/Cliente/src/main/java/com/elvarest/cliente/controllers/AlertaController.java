package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.AlertaDTO;
import com.elvarest.cliente.services.AlertaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/alertas")
public class AlertaController extends BaseController<AlertaDTO, Long> {

    public AlertaController(AlertaService service) {
        super(service);
        initController(new AlertaDTO(), "Lista de alertas", "Editar alerta", "alertas/");
    }
}
