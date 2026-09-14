package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.CargoDTO;
import org.springframework.stereotype.Service;

@Service
public class CargoService extends BaseService<CargoDTO, Long> {

    private static final String API_URL = "http://localhost:8080/cargos";

    public CargoService() {
        super(API_URL, CargoDTO.class);
    }
}
