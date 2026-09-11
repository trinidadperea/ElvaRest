package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.services.TipoCertificadoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tipos-certificado")
public class TipoCertificadoController extends BaseController<TipoCertificado, Long> {

    public TipoCertificadoController(TipoCertificadoService service) {
        super(service);
    }
}
