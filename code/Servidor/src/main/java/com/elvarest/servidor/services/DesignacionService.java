package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;

@Service
public class DesignacionService extends BaseService<Designacion, Long> {

    public DesignacionService(DesignacionRepository repository) {
        super(repository);
    }
}
