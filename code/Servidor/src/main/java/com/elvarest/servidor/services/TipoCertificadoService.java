package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.springframework.stereotype.Service;

@Service
public class TipoCertificadoService extends BaseService<TipoCertificado, Long> {
    private final TipoCertificadoRepository tipoCertificadoRepository;

    public TipoCertificadoService(TipoCertificadoRepository repository) {
        super(repository);
        this.tipoCertificadoRepository = repository;
    }
    @Override
    protected void validar(TipoCertificado entidad) throws ErrorServiceException {
        tipoCertificadoRepository.findByCodigo(entidad.getCodigo())
                .filter(existente -> !existente.getId().equals(entidad.getId()))
                .ifPresent(existente -> {
                    throw new RuntimeException("Ya existe un tipo de certificado con ese código");
                });
    }
}
