package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.TipoCertificadoDTO;
import org.springframework.stereotype.Service;

@Service
public class TipoCertificadoService extends BaseService<TipoCertificadoDTO, Long> {

    private static final String API_URL = "http://localhost:8080/tipos-certificado";

    public TipoCertificadoService() {
        super(API_URL, TipoCertificadoDTO.class);
    }
}
