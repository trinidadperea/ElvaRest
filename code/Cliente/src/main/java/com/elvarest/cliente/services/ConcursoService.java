package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.ConcursoDTO;
import org.springframework.stereotype.Service;

@Service
public class ConcursoService extends BaseService<ConcursoDTO, Long> {

    private static final String API_URL = "http://localhost:8080/concursos";

    public ConcursoService() {
        super(API_URL, ConcursoDTO.class);
    }
}
