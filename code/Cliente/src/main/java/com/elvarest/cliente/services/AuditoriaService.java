package com.elvarest.cliente.services;

import com.elvarest.cliente.auth.AuthService;
import com.elvarest.cliente.dto.AuditoriaDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * Consulta del historial de cambios. Servicio de SOLO LECTURA: a propósito no extiende
 * BaseService, de modo que alta, modificar y bajaLogica no existen en esta clase y no hay
 * forma de escribir en la auditoría desde el Cliente. Solo emite peticiones GET.
 */
@Service
public class AuditoriaService {

    private static final String API_URL = "http://localhost:8080/auditoria";

    private final RestTemplate restTemplate = new RestTemplate();
    private final AuthService authService;

    public AuditoriaService(AuthService authService) {
        this.authService = authService;
    }

    /** Historial completo, del más reciente al más antiguo (el orden lo resuelve el Servidor). */
    public List<AuditoriaDTO> listar() {
        HttpEntity<Void> entity = new HttpEntity<>(authService.authHeaders());
        ResponseEntity<AuditoriaDTO[]> response = restTemplate.exchange(
                API_URL, HttpMethod.GET, entity, AuditoriaDTO[].class);
        AuditoriaDTO[] cuerpo = response.getBody();
        return cuerpo == null ? List.of() : Arrays.asList(cuerpo);
    }

    /** Un evento por su uuid. El uuid viaja como variable de URI (se codifica, no se concatena). */
    public AuditoriaDTO obtener(String uuid) {
        HttpEntity<Void> entity = new HttpEntity<>(authService.authHeaders());
        ResponseEntity<AuditoriaDTO> response = restTemplate.exchange(
                API_URL + "/{uuid}", HttpMethod.GET, entity, AuditoriaDTO.class, uuid);
        return response.getBody();
    }
}