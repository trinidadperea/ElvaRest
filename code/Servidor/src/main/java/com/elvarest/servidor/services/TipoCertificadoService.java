package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.springframework.stereotype.Service;

@Service
public class TipoCertificadoService extends BaseService<TipoCertificado, Long> {

    public TipoCertificadoService(TipoCertificadoRepository repository) {
        super(repository);
    }
}
