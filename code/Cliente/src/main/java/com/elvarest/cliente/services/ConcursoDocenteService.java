package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import org.springframework.stereotype.Service;

@Service
public class ConcursoDocenteService extends BaseService<ConcursoDocenteDTO, Long> {

    private static final String API_URL = "http://localhost:8080/concursos-docentes";

    public ConcursoDocenteService() {
        super(API_URL, ConcursoDocenteDTO.class);
    }
}
