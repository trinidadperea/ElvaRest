package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConcursoDocenteService
        extends BaseService<ConcursoDocente, Long> {

    private final ConcursoDocenteRepository concursoDocenteRepository;

    public ConcursoDocenteService(
            ConcursoDocenteRepository concursoDocenteRepository) {

        super(concursoDocenteRepository);
        this.concursoDocenteRepository = concursoDocenteRepository;
    }

    public List<ConcursoDocente> obtenerOrdenMerito(Long concursoId) {
        return concursoDocenteRepository
                .findByConcursoIdOrderByDocentePuntajeDesc(concursoId);
    }

    public boolean docenteYaPostulado(
            Long concursoId,
            Long docenteId) {

        return concursoDocenteRepository
                .existsByConcursoIdAndDocenteId(
                        concursoId,
                        docenteId
                );
    }
}