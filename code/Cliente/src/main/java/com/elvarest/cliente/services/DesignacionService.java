package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.DesignacionDTO;
import org.springframework.stereotype.Service;

@Service
public class DesignacionService extends BaseService<DesignacionDTO, Long> {

    private static final String API_URL = "http://localhost:8080/designaciones";

    public DesignacionService() {
        super(API_URL, DesignacionDTO.class);
    }
}
