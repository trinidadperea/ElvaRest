package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.DocenteDTO;
import org.springframework.stereotype.Service;

@Service
public class DocenteService extends BaseService<DocenteDTO, Long> {

    private static final String API_URL = "http://localhost:8080/docentes";

    public DocenteService() {
        super(API_URL, DocenteDTO.class);
    }
}
