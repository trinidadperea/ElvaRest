package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.services.ConcursoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concursos")
public class ConcursoController extends BaseController<Concurso, Long> {

    private final ConcursoService concursoService;

    public ConcursoController(ConcursoService service) {
        super(service);
        this.concursoService = service;
    }

    /** Reglas de eliminación: validadas en el servidor antes de dar de baja el registro. */
    @Override
    protected void preDelete(Long id) throws ErrorServiceException {
        concursoService.validarEliminacion(id);
    }

    /*@GetMapping("/{id}/primero")
    public ResponseEntity<Docente> obtenerPrimero(
            @PathVariable Long id) {

        Docente docente =
                concursoService.obtenerPrimero(id);

        if (docente == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(docente);
    }

    @GetMapping("/{id}/siguiente/{docenteId}")
    public ResponseEntity<Docente> obtenerSiguiente(
            @PathVariable Long id,
            @PathVariable Long docenteId) {

        Docente docente =
                concursoService.obtenerSiguienteEnOrden(
                        id,
                        docenteId
                );

        if (docente == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(docente);
    }*/

}
