package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.CursoDTO;
import com.elvarest.cliente.dto.MateriaDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.CursoService;
import com.elvarest.cliente.services.MateriaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/academica")
public class AcademicaController extends BaseController<CursoDTO, Long> {

    private final MateriaService materiaService;

    public AcademicaController(CursoService service, MateriaService materiaService) {
        super(service);
        this.materiaService = materiaService;
        initController(new CursoDTO(), "Lista académica", "Editar curso", "academica/");
    }

    /** Agrega al detalle las materias asociadas al curso consultado. */
    @Override
    protected void postConsulta(CursoDTO found) throws ErrorServiceException {
        if (found == null || found.getId() == null) {
            return;
        }
        try {
            List<MateriaDTO> materias = materiaService.listarActivos().stream()
                    .filter(materia -> materia.getCurso() != null
                            && Objects.equals(materia.getCurso().getId(), found.getId()))
                    .toList();
            model.addAttribute("materias", materias);
        } catch (Exception e) {
            model.addAttribute("materias", List.of());
        }
    }
}
