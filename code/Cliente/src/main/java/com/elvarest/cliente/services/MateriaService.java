package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.MateriaDTO;
import org.springframework.stereotype.Service;

@Service
public class MateriaService extends BaseService<MateriaDTO, Long> {

    private static final String API_URL = "http://localhost:8080/materias";

    public MateriaService() {
        super(API_URL, MateriaDTO.class);
    }
}
