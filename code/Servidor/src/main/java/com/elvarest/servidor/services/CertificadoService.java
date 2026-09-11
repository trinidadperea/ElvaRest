package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.repositories.CertificadoRepository;
import org.springframework.stereotype.Service;

@Service
public class CertificadoService extends BaseService<Certificado, Long> {

    public CertificadoService(CertificadoRepository repository) {
        super(repository);
    }
}
