package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.ConcursoDocente;

import java.util.List;

public interface ConcursoDocenteRepository
        extends BaseRepository<ConcursoDocente, Long> {

    List<ConcursoDocente> findByConcursoIdOrderByDocentePuntajeDesc(
            Long concursoId
    );

    boolean existsByConcursoIdAndDocenteId(
            Long concursoId,
            Long docenteId
    );
}