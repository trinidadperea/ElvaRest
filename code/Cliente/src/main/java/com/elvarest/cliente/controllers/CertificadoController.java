package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.TipoCertificadoDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.DocenteService;
import com.elvarest.cliente.services.TipoCertificadoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/certificados")
public class CertificadoController extends BaseController<CertificadoDTO, Long> {

    private final DocenteService docenteService;
    private final TipoCertificadoService tipoCertificadoService;

    public CertificadoController(CertificadoService service, DocenteService docenteService,
                                 TipoCertificadoService tipoCertificadoService) {
        super(service);
        this.docenteService = docenteService;
        this.tipoCertificadoService = tipoCertificadoService;
        initController(new CertificadoDTO(), "Lista de certificados", "Editar certificado", "certificados/");
    }

    @GetMapping("/nuevo/docente/{docenteId}")
    public String nuevoParaDocente(@PathVariable Long docenteId, Model model) {
        DocenteDTO docente = docenteService.obtener(docenteId);
        if (docente == null) {
            throw new IllegalArgumentException("No se encontró el docente: " + docenteId);
        }

        CertificadoDTO certificado = new CertificadoDTO();
        certificado.setDocente(docente);
        certificado.setTipoCertificado(new TipoCertificadoDTO());
        model.addAttribute("item", certificado);
        model.addAttribute("isDisabled", false);
        model.addAttribute("titleEdit", "Nuevo certificado");
        model.addAttribute("nameEntityLower", "certificado");
        addFormOptions(model);
        return viewCreate;
    }

    @Override
    protected void preAlta() throws ErrorServiceException {
        prepararRelaciones();
        addFormOptions(model);
    }

    @Override
    protected void preModificacion() throws ErrorServiceException {
        prepararRelaciones();
        addFormOptions(model);
    }

    private void prepararRelaciones() {
        CertificadoDTO certificado = (CertificadoDTO) model.getAttribute("item");
        if (certificado.getDocente() == null) {
            certificado.setDocente(new DocenteDTO());
        }
        if (certificado.getTipoCertificado() == null) {
            certificado.setTipoCertificado(new TipoCertificadoDTO());
        }
    }

    private void addFormOptions(Model model) {
        model.addAttribute("docentes", docenteService.listarActivos());
        model.addAttribute("tiposCertificado", tipoCertificadoService.listarActivos());
    }
}
