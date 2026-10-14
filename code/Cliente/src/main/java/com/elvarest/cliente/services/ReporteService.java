package com.elvarest.cliente.services;

import com.elvarest.cliente.auth.AuthService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Descarga de reportes generados por el Servidor (PDF).
 */
@Service
public class ReporteService {

    private static final String API_URL = "http://localhost:8080/reportes";

    private final RestTemplate restTemplate = new RestTemplate();
    private final AuthService authService;

    public ReporteService(AuthService authService) {
        this.authService = authService;
    }

    /** PDF del concurso: datos, resumen de horas y postulaciones (incluidas las canceladas). */
    public byte[] pdfConcurso(Long concursoId) {
        HttpHeaders headers = authService.authHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_PDF));

        return restTemplate.exchange(
                API_URL + "/concurso/" + concursoId,
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                byte[].class
        ).getBody();
    }
}
