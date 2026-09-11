package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.UsuarioDTO;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService extends BaseService<UsuarioDTO, Long> {

    private static final String API_URL = "http://localhost:8080/usuarios";

    public UsuarioService() {
        super(API_URL, UsuarioDTO.class);
    }
}
