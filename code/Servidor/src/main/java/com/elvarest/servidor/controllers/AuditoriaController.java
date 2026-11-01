package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Auditoria;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.AuditoriaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Historial de cambios del sistema: solo lectura (GET), exclusivo del ADMIN por SecurityConfig.
 * No hereda BaseController para no exponer los verbos de escritura del CRUD.
 */
@RestController
@RequestMapping("/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping("")
    public ResponseEntity<?> getAll() {
        try {
            List<Auditoria> auditorias = service.listarActivos();
            return ResponseEntity.status(HttpStatus.OK).body(auditorias);
        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error de Sistema"));
        }
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<?> getOne(@PathVariable String uuid) {
        try {
            Auditoria auditoria = service.getOnePorUuid(uuid);
            return ResponseEntity.status(HttpStatus.OK).body(auditoria);
        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Entidad no encontrada"));
        }
    }

    /** Mismo formato de error que el resto de los controladores. */
    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
