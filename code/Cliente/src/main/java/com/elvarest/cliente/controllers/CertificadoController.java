package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.TipoCertificadoDTO;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.TipoCertificadoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Certificados de una postulación. Las reglas (tipo, carácter, vencimiento, historial y alertas)
 * están en el Servidor; este controlador solo arma la vista y delega.
 */
@Controller
@RequestMapping("/certificados")
public class CertificadoController extends BaseController<CertificadoDTO, Long> {

    private final CertificadoService certificadoService;
    private final ConcursoDocenteService concursoDocenteService;
    private final TipoCertificadoService tipoCertificadoService;

    public CertificadoController(CertificadoService service, ConcursoDocenteService concursoDocenteService,
                                 TipoCertificadoService tipoCertificadoService) {
        super(service);
        this.certificadoService = service;
        this.concursoDocenteService = concursoDocenteService;
        this.tipoCertificadoService = tipoCertificadoService;
        initController(new CertificadoDTO(), "Lista de certificados", "Certificado", "certificados/");
    }

    /** Pantalla de carga / renovación para una postulación (se llega desde el detalle del docente). */
    @GetMapping("/nuevo/postulacion/{postulacionId}")
    public String nuevoParaPostulacion(@PathVariable Long postulacionId,
                                       @RequestParam(value = "tipo", required = false) Long tipoId,
                                       Model model) {
        ConcursoDocenteDTO postulacion = concursoDocenteService.obtener(postulacionId);
        if (postulacion == null) {
            throw new IllegalArgumentException("No se encontró la postulación: " + postulacionId);
        }
        CertificadoDTO certificado = new CertificadoDTO();
        certificado.setConcursoDocente(postulacion);
        TipoCertificadoDTO tipo = new TipoCertificadoDTO();
        tipo.setId(tipoId);
        certificado.setTipoCertificado(tipo);

        model.addAttribute("item", certificado);
        model.addAttribute("postulacion", postulacion);
        model.addAttribute("certificados", certificadoService.listarPorPostulacion(postulacionId));
        model.addAttribute("tiposCertificado", tipoCertificadoService.listarActivos());
        return viewCreate;
    }

    /**
     * Guarda el certificado de una postulación y vuelve a la pantalla de origen
     * ("concurso" = paso 2 del concurso; cualquier otro valor = detalle del docente).
     */
    @PostMapping("/postulacion/{postulacionId}")
    public String guardarParaPostulacion(@PathVariable Long postulacionId,
                                         @ModelAttribute("item") CertificadoDTO certificado,
                                         @RequestParam(value = "origen", required = false) String origen,
                                         RedirectAttributes attributes) {
        ConcursoDocenteDTO postulacion = concursoDocenteService.obtener(postulacionId);
        try {
            ConcursoDocenteDTO referencia = new ConcursoDocenteDTO();
            referencia.setId(postulacionId);
            certificado.setConcursoDocente(referencia);
            certificadoService.alta(certificado);
            attributes.addFlashAttribute("msgExito", "Certificado registrado correctamente.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            if (!"concurso".equals(origen)) {
                return "redirect:/certificados/nuevo/postulacion/" + postulacionId;
            }
        }
        if ("concurso".equals(origen) && postulacion != null && postulacion.getConcurso() != null) {
            return "redirect:/concursos/" + postulacion.getConcurso().getId()
                    + "/postulaciones/" + postulacionId + "/certificados";
        }
        return postulacion != null && postulacion.getDocente() != null
                ? "redirect:/docentes/" + postulacion.getDocente().getId()
                : redirectList;
    }

    /** El alta genérica (sin postulación) ya no existe: se carga desde el concurso o desde el docente. */
    @Override
    @GetMapping({"/alta", "/nuevo"})
    public String crear(Model model) {
        return redirectList;
    }

    /** Los certificados no se editan (se renuevan cargando uno nuevo): se muestra el detalle. */
    @Override
    @GetMapping({"/modificar/{id}", "/{id}/editar"})
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        return "redirect:/certificados/" + id;
    }
}