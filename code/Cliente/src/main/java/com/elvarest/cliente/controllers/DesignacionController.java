package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.AlertaService;
import com.elvarest.cliente.services.DesignacionService;
import com.elvarest.cliente.services.DocenteService;
import com.elvarest.cliente.services.LicenciaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/designaciones")
public class DesignacionController extends BaseController<DesignacionDTO, Long> {

    private AlertaService alertaService;
    private DocenteService docenteService;
    private LicenciaService licenciaService;

    public DesignacionController(DesignacionService service) {
        super(service);
        initController(new DesignacionDTO(), "Lista de designaciones", "Editar designación", "designaciones/");
    }

    @Override
    protected void preAlta() throws ErrorServiceException {
        //super.preAlta();

    }
}
