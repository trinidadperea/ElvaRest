package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.repositories.ConcursoRepository;
import org.springframework.stereotype.Service;

@Service
public class ConcursoService extends BaseService<Concurso, Long> {

    public ConcursoService(ConcursoRepository repository) {
        super(repository);
    }
}
