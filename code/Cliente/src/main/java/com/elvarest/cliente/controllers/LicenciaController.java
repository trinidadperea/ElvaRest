package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.LicenciaDTO;
import com.elvarest.cliente.services.LicenciaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/licencias")
public class LicenciaController extends BaseController<LicenciaDTO, Long> {

    public LicenciaController(LicenciaService service) {
        super(service);
        initController(new LicenciaDTO(), "Lista de licencias", "Editar licencia", "licencias/");
    }
}
