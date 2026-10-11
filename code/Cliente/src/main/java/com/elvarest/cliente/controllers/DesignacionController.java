package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.DesignacionService;
import com.elvarest.cliente.services.DocenteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/designaciones")
public class DesignacionController extends BaseController<DesignacionDTO, Long> {

    private final DocenteService docenteService;
    private final HttpServletRequest request;

    public DesignacionController(DesignacionService service, DocenteService docenteService, HttpServletRequest request) {
        super(service);
        this.docenteService = docenteService;
        this.request = request;
        initController(new DesignacionDTO(), "Lista de designaciones", "Editar designación", "designaciones/");
    }

    @Override
    protected void preAlta() throws ErrorServiceException {
        model.addAttribute("docentes", docenteService.listarActivos());
        conservarCargoOrigen();
    }

    @Override
    protected void preModificacion() throws ErrorServiceException {
        model.addAttribute("docentes", docenteService.listarActivos());
        conservarCargoOrigen();
    }

    /**
     * Conserva el contexto de "Cargo → Ver" al editar una designación: si el enlace de
     * origen envió ?cargoId=..., se expone como "cargoOrigenId" para que el botón
     * Cancelar y el guardado exitoso del formulario vuelvan al detalle de ese cargo.
     * Sin el parámetro no agrega nada al modelo, por lo que los demás accesos al
     * formulario no cambian.
     */
    private void conservarCargoOrigen() {
        if (model == null) {
            return;
        }
        String cargoId = request.getParameter("cargoId");
        if (cargoId != null && cargoId.matches("\\d+")) {
            model.addAttribute("cargoOrigenId", cargoId);
        }
    }

    /**
     * Mismo destino que el botón Cancelar tras guardar: si la edición vino desde
     * "Cargo → Ver" (el formulario envió cargoId), el guardado exitoso vuelve al
     * detalle de ese cargo; sin el parámetro mantiene el redirect original al
     * listado de designaciones.
     */
    private String destinoTrasGuardar() {
        String cargoId = request.getParameter("cargoId");
        if (cargoId != null && cargoId.matches("\\d+")) {
            return "redirect:/cargos/" + cargoId;
        }
        return redirectList;
    }

    /**
     * Al modificar una designación se conservan sus alertas y licencias asociadas: el
     * formulario solo edita los datos propios y el backend reemplaza la entidad completa.
     */
    @Override
    @PostMapping("/actualizar")
    public String actualizar(@ModelAttribute("item") DesignacionDTO entidad,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             RedirectAttributes attributes, Model model) {
        try {
            this.model = model;
            if (entidad.getId() != null) {
                DesignacionDTO original = service.obtener(entidad.getId());
                if (original != null) {
                    entidad.setAlertas(original.getAlertas());
                    entidad.setLicencias(original.getLicencias());
                    entidad.setEliminado(original.getEliminado());
                }
            }
            if (entidad.getId() == null) {
                service.alta(entidad);
            } else {
                service.modificar(entidad.getId(), entidad);
            }
            attributes.addFlashAttribute("msgExito", "La acción fue realizada correctamente.");
            return destinoTrasGuardar();
        } catch (ErrorServiceException e) {
            model.addAttribute("msgError", e.getMessage());
            repoblarFormulario(entidad, model);
            return entidad.getId() == null ? viewCreate : viewEdit;
        } catch (Exception e) {
            model.addAttribute("msgError", "Error de Sistema");
            repoblarFormulario(entidad, model);
            return entidad.getId() == null ? viewCreate : viewEdit;
        }
    }
}
