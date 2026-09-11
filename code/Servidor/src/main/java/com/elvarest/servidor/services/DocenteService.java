package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.repositories.DocenteRepository;
import org.springframework.stereotype.Service;

@Service
public class DocenteService extends BaseService<Docente, Long> {

    public DocenteService(DocenteRepository repository) {
        super(repository);
    }
}
