package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoRepository;
import org.springframework.stereotype.Service;

@Service
public class ConcursoService extends BaseService<Concurso, Long> {

    public ConcursoService(ConcursoRepository repository) {
        super(repository);
    }

    @Override
    protected void validar(Concurso concurso) throws ErrorServiceException {
        if (concurso.getEstadoConcurso() == null) {
            throw new ErrorServiceException("Debe seleccionar un estado para el concurso.");
        }
    }
}
