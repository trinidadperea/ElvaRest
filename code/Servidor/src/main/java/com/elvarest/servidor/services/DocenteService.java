package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.DocenteRepository;
import org.springframework.stereotype.Service;

@Service
public class DocenteService extends BaseService<Docente, Long> {

    public DocenteService(DocenteRepository repository) {
        super(repository);
    }

    @Override
    protected void validar(Docente entidad) throws ErrorServiceException {
        if (entidad.getPuntaje() < 0) {
            throw new ErrorServiceException("El puntaje no puede ser negativo");
        }
        if (entidad.getArea() == null || entidad.getArea().isBlank()) {
            throw new ErrorServiceException("El área es obligatoria");
        }
    }

    @Override
    protected void preAlta(Docente entidad) throws ErrorServiceException {
        entidad.setActivo(true);
    }
}
