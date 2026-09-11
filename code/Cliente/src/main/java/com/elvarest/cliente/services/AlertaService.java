package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.AlertaDTO;
import org.springframework.stereotype.Service;

@Service
public class AlertaService extends BaseService<AlertaDTO, Long> {

    private static final String API_URL = "http://localhost:8080/alertas";

    public AlertaService() {
        super(API_URL, AlertaDTO.class);
    }
}
