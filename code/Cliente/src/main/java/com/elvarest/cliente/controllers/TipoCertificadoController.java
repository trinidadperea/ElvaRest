package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.TipoCertificadoDTO;
import com.elvarest.cliente.services.TipoCertificadoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** ABM de tipos de certificado-  Alta, edición y detalle usan el mismo formulario. */
@Controller
@RequestMapping("/tipos-certificado")
public class TipoCertificadoController extends BaseController<TipoCertificadoDTO, Long> {

    public TipoCertificadoController(TipoCertificadoService service) {
        super(service);
        initController(new TipoCertificadoDTO(), "Tipos de certificado", "Tipo de certificado", "tipos-certificado/");
        this.viewEdit = this.viewCreate;
        this.viewDetail = this.viewCreate;
    }
}
