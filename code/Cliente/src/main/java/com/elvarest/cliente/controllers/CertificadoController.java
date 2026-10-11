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

import java.time.LocalDate;

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

    /**
     * Anticipa las validaciones del Servidor referidas a la fecha de vencimiento para
     * mostrar siempre el mensaje específico: en alta el Cliente no propaga el mensaje
     * del Servidor y en modificación el Servidor lo reemplaza por uno genérico.
     */
    @Override
    protected void preActualziacion() throws ErrorServiceException {
        CertificadoDTO certificado = (CertificadoDTO) model.getAttribute("item");
        if (certificado == null) {
            return;
        }
        LocalDate vencimiento = certificado.getFechaVencimiento();
        if (certificado.isRequiereVencimiento() && vencimiento == null) {
            throw new ErrorServiceException("Si el certificado requiere vencimiento, debe indicarse la fecha de vencimiento");
        }
        if (!certificado.isRequiereVencimiento() && vencimiento != null) {
            throw new ErrorServiceException("Un certificado que no requiere vencimiento no debe tener fecha de vencimiento");
        }
        if (vencimiento != null && certificado.getFechaEmision() != null
                && !vencimiento.isAfter(certificado.getFechaEmision())) {
            throw new ErrorServiceException("La fecha de vencimiento debe ser posterior a la fecha de emisión");
        }
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
