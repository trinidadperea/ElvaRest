package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.repositories.CargoRepository;
import org.springframework.stereotype.Service;

@Service
public class CargoService extends BaseService<Cargo, Long> {

    public CargoService(CargoRepository repository) {
        super(repository);
    }
}
