package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CertificadoRepository;
import org.springframework.stereotype.Service;

@Service
public class CertificadoService extends BaseService<Certificado, Long> {

    public CertificadoService(CertificadoRepository repository) {
        super(repository);
    }

    @Override
    protected void validar(Certificado entidad) throws ErrorServiceException {
        if (entidad.getDocente() == null) {
            throw new ErrorServiceException("El certificado debe estar asociado a un docente");
        }
        if (entidad.getTipoCertificado() == null) {
            throw new ErrorServiceException("El certificado debe tener un tipo de certificado");
        }
        if (entidad.isRequiereVencimiento() && entidad.getFechaVencimiento() == null) {
            throw new ErrorServiceException("Si el certificado requiere vencimiento, debe indicarse la fecha de vencimiento");
        }
        if (!entidad.isRequiereVencimiento() && entidad.getFechaVencimiento() != null) {
            throw new ErrorServiceException("Un certificado que no requiere vencimiento no debe tener fecha de vencimiento");
        }
        if (entidad.isRequiereVencimiento()
                && entidad.getFechaVencimiento() != null
                && !entidad.getFechaVencimiento().isAfter(entidad.getFechaEmision())) {
            throw new ErrorServiceException("La fecha de vencimiento debe ser posterior a la fecha de emisión");
        }
    }
}
