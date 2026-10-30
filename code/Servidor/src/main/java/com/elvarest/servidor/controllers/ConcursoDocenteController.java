package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.ConcursoDocenteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concursos-docentes")
public class ConcursoDocenteController extends BaseController<ConcursoDocente, Long> {

    private final ConcursoDocenteService concursoDocenteService;

    public ConcursoDocenteController(ConcursoDocenteService service) {
        super(service);
        this.concursoDocenteService = service;
    }

    /**
     * Paso 3 del flujo: confirma la toma de posesión y, si el concurso quedó completo, lo cierra.
     * Es una sola operación en el servicio, así que una llamada directa a la API no puede dejar la
     * designación creada sin la postulación actualizada (ni al revés).
     */
    @PostMapping("/{postulacionId}/posesion")
    public ResponseEntity<?> confirmarPosesion(@PathVariable Long postulacionId,
                                               @RequestParam Long concursoId,
                                               @RequestBody Designacion designacion) {
        try {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(concursoDocenteService.confirmarPosesion(concursoId, postulacionId, designacion));
        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
    }
}
