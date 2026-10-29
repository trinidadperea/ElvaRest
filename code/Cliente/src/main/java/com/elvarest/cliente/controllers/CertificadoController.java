package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.TipoCertificadoDTO;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoDocenteService;
import com.elvarest.cliente.services.TipoCertificadoService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final HttpServletRequest request;

    public CertificadoController(CertificadoService service, ConcursoDocenteService concursoDocenteService,
                                 TipoCertificadoService tipoCertificadoService, HttpServletRequest request) {
        super(service);
        this.certificadoService = service;
        this.concursoDocenteService = concursoDocenteService;
        this.tipoCertificadoService = tipoCertificadoService;
        this.request = request;
        initController(new CertificadoDTO(), "Lista de certificados", "Certificado", "certificados/");
    }

    /**
     * Conserva el contexto de origen al abrir el detalle de un certificado:
     * - ?origen=alerta&alerta={id}: se vuelve al detalle de esa alerta («Volver»);
     * - ?origen=docente: se vuelve a la ficha del docente («Volver al docente»).
     * Sin el parámetro, el detalle mantiene el retorno al listado de certificados (accesos desde
     * el listado o desde un concurso).
     */
    @Override
    protected void postConsulta(CertificadoDTO found) {
        if (model == null) {
            return;
        }
        if ("docente".equals(request.getParameter("origen"))) {
            ConcursoDocenteDTO postulacion = found.getConcursoDocente();
            if (postulacion != null && postulacion.getDocente() != null) {
                model.addAttribute("docenteOrigenId", postulacion.getDocente().getId());
            }
            return;
        }
        if ("alerta".equals(request.getParameter("origen"))) {
            Long alertaId = parsearId(request.getParameter("alerta"));
            if (alertaId != null) {
                model.addAttribute("alertaOrigenId", alertaId);
            }
        }
    }

    /** Id numérico del parámetro de contexto; un valor inválido simplemente no habilita el retorno. */
    private Long parsearId(String valor) {
        try {
            return valor != null ? Long.valueOf(valor) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Pantalla de carga / renovación para una postulación.
     * - ?certificado={id}: renueva ese certificado; el tipo se hereda del original y queda fijo.
     * - ?tipo={id}: compatibilidad con enlaces anteriores; sólo preselecciona el tipo (editable).
     * Sin parámetros (ficha del docente) el tipo se elige del catálogo.
     */
    @GetMapping("/nuevo/postulacion/{postulacionId}")
    public String nuevoParaPostulacion(@PathVariable Long postulacionId,
                                       @RequestParam(value = "tipo", required = false) Long tipoId,
                                       @RequestParam(value = "certificado", required = false) Long certificadoId,
                                       Model model) {
        ConcursoDocenteDTO postulacion = concursoDocenteService.obtener(postulacionId);
        if (postulacion == null) {
            throw new IllegalArgumentException("No se encontró la postulación: " + postulacionId);
        }
        CertificadoDTO original = certificadoOriginal(certificadoId, postulacionId);
        CertificadoDTO certificado = new CertificadoDTO();
        certificado.setConcursoDocente(postulacion);
        if (original != null) {
            certificado.setTipoCertificado(original.getTipoCertificado());
        } else {
            TipoCertificadoDTO tipo = new TipoCertificadoDTO();
            tipo.setId(tipoId);
            certificado.setTipoCertificado(tipo);
        }

        model.addAttribute("item", certificado);
        model.addAttribute("postulacion", postulacion);
        model.addAttribute("certificados", certificadoService.listarPorPostulacion(postulacionId));
        model.addAttribute("tiposCertificado", tipoCertificadoService.listarActivos());
        model.addAttribute("tipoFijo", original != null ? original.getTipoCertificado() : null);
        model.addAttribute("certificadoOrigenId", original != null ? original.getId() : null);
        return viewCreate;
    }

    /**
     * Guarda el certificado de una postulación y vuelve a la pantalla de origen
     * ("concurso" = paso 2 del concurso; cualquier otro valor = detalle del docente).
     * En una renovación el tipo lo aporta el certificado original: el valor del formulario
     * no se usa, así que no puede cambiarse desde el navegador.
     */
    @PostMapping("/postulacion/{postulacionId}")
    public String guardarParaPostulacion(@PathVariable Long postulacionId,
                                         @ModelAttribute("item") CertificadoDTO certificado,
                                         @RequestParam(value = "origen", required = false) String origen,
                                         @RequestParam(value = "certificadoOrigen", required = false) Long certificadoOrigenId,
                                         RedirectAttributes attributes) {
        ConcursoDocenteDTO postulacion = concursoDocenteService.obtener(postulacionId);
        CertificadoDTO original = certificadoOriginal(certificadoOrigenId, postulacionId);
        if (original != null) {
            certificado.setTipoCertificado(original.getTipoCertificado());
        }
        try {
            ConcursoDocenteDTO referencia = new ConcursoDocenteDTO();
            referencia.setId(postulacionId);
            certificado.setConcursoDocente(referencia);
            certificadoService.alta(certificado);
            attributes.addFlashAttribute("msgExito", "Certificado registrado correctamente.");
        } catch (RuntimeException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
            if (!"concurso".equals(origen)) {
                // Un error de validación vuelve al formulario conservando el modo renovación.
                String renovacion = original != null ? "?certificado=" + original.getId() : "";
                return "redirect:/certificados/nuevo/postulacion/" + postulacionId + renovacion;
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

    /**
     * Certificado que se renueva: sólo si existe, pertenece a la postulación y tiene tipo.
     * Un id inválido o ajeno se ignora y el formulario queda en modo de carga normal.
     */
    private CertificadoDTO certificadoOriginal(Long certificadoId, Long postulacionId) {
        if (certificadoId == null) {
            return null;
        }
        try {
            CertificadoDTO original = certificadoService.obtener(certificadoId);
            if (original == null || original.getConcursoDocente() == null
                    || !postulacionId.equals(original.getConcursoDocente().getId())) {
                return null;
            }
            return original.getTipoCertificado() != null ? original : null;
        } catch (RuntimeException e) {
            return null;
        }
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