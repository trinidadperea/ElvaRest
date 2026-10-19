package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Reporte;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.ReporteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reportes")
public class ReporteController
        extends BaseController<Reporte, Long> {

    private final ReporteService reporteService;

    public ReporteController(
            ReporteService reporteService) {

        super(reporteService);
        this.reporteService = reporteService;
    }

    @GetMapping(
            value = "/concurso/{concursoId}",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> generarReporteConcurso(
            @PathVariable Long concursoId) {

        try {
            byte[] pdf = reporteService.generarReporteConcurso(concursoId);

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=reporte-concurso-"
                                    + concursoId
                                    + ".pdf"
                    )
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}