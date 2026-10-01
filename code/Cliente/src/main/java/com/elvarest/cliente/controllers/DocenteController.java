package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.DocenteService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Objects;

@Controller
@RequestMapping("/docentes")
public class DocenteController extends BaseController<DocenteDTO, Long> {

    private final CertificadoService certificadoService;

    public DocenteController(DocenteService service, CertificadoService certificadoService) {
        super(service);
        this.certificadoService = certificadoService;
        initController(new DocenteDTO(), "Lista de docentes", "Editar docente", "docentes/");
    }

    @Override
    protected void postConsulta(DocenteDTO docente) throws ErrorServiceException {
        model.addAttribute("certificados", certificadoService.listarActivos().stream()
                .filter(certificado -> certificado.getDocente() != null
                        && Objects.equals(certificado.getDocente().getId(), docente.getId()))
                .toList());
    }
}
