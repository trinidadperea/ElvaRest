package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import org.springframework.stereotype.Service;

@Service
public class ConcursoDocenteService extends BaseService<ConcursoDocente, Long> {

    public ConcursoDocenteService(ConcursoDocenteRepository repository) {
        super(repository);
    }
}
