package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Curso;
import com.elvarest.servidor.repositories.CursoRepository;
import org.springframework.stereotype.Service;

@Service
public class CursoService extends BaseService<Curso, Long> {

    public CursoService(CursoRepository repository) {
        super(repository);
    }
}
