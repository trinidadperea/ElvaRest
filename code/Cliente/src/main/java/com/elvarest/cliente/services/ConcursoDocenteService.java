package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DesignacionDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class ConcursoDocenteService extends BaseService<ConcursoDocenteDTO, Long> {

    private static final String API_URL = "http://localhost:8080/concursos-docentes";

    public ConcursoDocenteService() {
        super(API_URL, ConcursoDocenteDTO.class);
    }

    /**
     * Confirma la toma de posesión en el Servidor: allí se crea la designación, se vincula al cargo,
     * se avanza la postulación y se cierra el concurso si quedó completo. Es una sola operación
     * atómica, así que el Cliente no repite ninguna de esas escrituras.
     *
     * @return la designación creada por el Servidor
     */
    public DesignacionDTO confirmarPosesion(Long concursoId, Long postulacionId, DesignacionDTO designacion) {
        HttpEntity<DesignacionDTO> entity = new HttpEntity<>(designacion, authService.authHeaders());
        try {
            ResponseEntity<DesignacionDTO> response = restTemplate.exchange(
                    API_URL + "/" + postulacionId + "/posesion?concursoId=" + concursoId,
                    HttpMethod.POST,
                    entity,
                    DesignacionDTO.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw errorDelServidor(e);
        }
    }
}
