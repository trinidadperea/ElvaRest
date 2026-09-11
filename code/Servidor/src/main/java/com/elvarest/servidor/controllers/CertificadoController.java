package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.services.CertificadoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/certificados")
public class CertificadoController extends BaseController<Certificado, Long> {

    public CertificadoController(CertificadoService service) {
        super(service);
    }
}
