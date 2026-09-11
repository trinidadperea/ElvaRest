package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.CursoDTO;
import org.springframework.stereotype.Service;

@Service
public class CursoService extends BaseService<CursoDTO, Long> {

    private static final String API_URL = "http://localhost:8080/cursos";

    public CursoService() {
        super(API_URL, CursoDTO.class);
    }
}
