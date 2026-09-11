package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.LicenciaDTO;
import org.springframework.stereotype.Service;

@Service
public class LicenciaService extends BaseService<LicenciaDTO, Long> {

    private static final String API_URL = "http://localhost:8080/licencias";

    public LicenciaService() {
        super(API_URL, LicenciaDTO.class);
    }
}
