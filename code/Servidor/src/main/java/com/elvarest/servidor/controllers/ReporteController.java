package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Reporte;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.ReporteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            value = "/concurso/{concursoUuid}",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<byte[]> generarReporteConcurso(
            @PathVariable String concursoUuid) {

        try {
            byte[] pdf = reporteService.generarReporteConcurso(concursoUuid);

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=reporte-concurso-"
                                    + concursoUuid
                                    + ".pdf"
                    )
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Exportación Excel del reporte de concurso: mismo contenido que el PDF, restringida al ADMIN
     * tanto por el matcher de SecurityConfig (/reportes/excel/**) como por esta anotación.
     */
    @GetMapping(
            value = "/excel/concurso/{concursoUuid}",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> generarReporteConcursoExcel(
            @PathVariable String concursoUuid) {

        try {
            byte[] excel = reporteService.generarReporteConcursoExcel(concursoUuid);

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=reporte-concurso-"
                                    + concursoUuid
                                    + ".xlsx"
                    )
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);

        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}