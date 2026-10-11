package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.MateriaDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.CursoService;
import com.elvarest.cliente.services.MateriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/materias")
public class MateriaController extends BaseController<MateriaDTO, Long> {

    private final CursoService cursoService;

    public MateriaController(MateriaService service, CursoService cursoService) {
        super(service);
        this.cursoService = cursoService;
        initController(new MateriaDTO(), "Lista de materias", "Editar materia", "materias/");
    }

    /** Carga los cursos activos para el selector de curso del formulario de alta. */
    @Override
    protected void preAlta() throws ErrorServiceException {
        cargarCursos();
    }

    /** Carga los cursos activos para el selector de curso del formulario de edición. */
    @Override
    protected void preModificacion() throws ErrorServiceException {
        cargarCursos();
    }

    private void cargarCursos() {
        try {
            model.addAttribute("cursos", cursoService.listarActivos());
        } catch (Exception e) {
            model.addAttribute("cursos", List.of());
        }
    }

    /**
     * Al guardar con el selector de curso vacío llega una referencia con identificador nulo,
     * que se normaliza a curso nulo para no referenciar un curso inexistente.
     */
    @Override
    @PostMapping("/actualizar")
    public String actualizar(@ModelAttribute("item") MateriaDTO entidad,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             RedirectAttributes attributes, Model model) {
        if (entidad.getCurso() != null && entidad.getCurso().getId() == null) {
            entidad.setCurso(null);
        }
        return super.actualizar(entidad, file, attributes, model);
    }
}
