package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.services.ConcursoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/concursos")
public class ConcursoController
        extends BaseController<Concurso, Long> {

    private final ConcursoService concursoService;

    public ConcursoController(ConcursoService concursoService) {
        super(concursoService);
        this.concursoService = concursoService;
    }

    @GetMapping("/{id}/primero")
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
    }
}