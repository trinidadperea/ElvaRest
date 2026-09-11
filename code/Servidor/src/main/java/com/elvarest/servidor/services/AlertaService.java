package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.repositories.AlertaRepository;
import org.springframework.stereotype.Service;

@Service
public class AlertaService extends BaseService<Alerta, Long> {

    public AlertaService(AlertaRepository repository) {
        super(repository);
    }
}
