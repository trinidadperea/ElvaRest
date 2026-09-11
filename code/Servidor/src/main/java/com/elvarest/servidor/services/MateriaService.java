package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.repositories.MateriaRepository;
import org.springframework.stereotype.Service;

@Service
public class MateriaService extends BaseService<Materia, Long> {

    public MateriaService(MateriaRepository repository) {
        super(repository);
    }
}
