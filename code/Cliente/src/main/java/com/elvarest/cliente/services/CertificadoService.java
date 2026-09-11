package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.CertificadoDTO;
import org.springframework.stereotype.Service;

@Service
public class CertificadoService extends BaseService<CertificadoDTO, Long> {

    private static final String API_URL = "http://localhost:8080/certificados";

    public CertificadoService() {
        super(API_URL, CertificadoDTO.class);
    }
}
