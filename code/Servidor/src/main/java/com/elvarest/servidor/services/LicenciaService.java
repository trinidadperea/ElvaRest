package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Licencia;
import com.elvarest.servidor.repositories.LicenciaRepository;
import org.springframework.stereotype.Service;

@Service
public class LicenciaService extends BaseService<Licencia, Long> {

    public LicenciaService(LicenciaRepository repository) {
        super(repository);
    }
}
