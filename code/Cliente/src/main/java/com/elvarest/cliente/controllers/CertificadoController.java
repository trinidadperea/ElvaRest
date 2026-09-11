package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.services.CertificadoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/certificados")
public class CertificadoController extends BaseController<CertificadoDTO, Long> {

    public CertificadoController(CertificadoService service) {
        super(service);
        initController(new CertificadoDTO(), "Lista de certificados", "Editar certificado", "certificados/");
    }
}
