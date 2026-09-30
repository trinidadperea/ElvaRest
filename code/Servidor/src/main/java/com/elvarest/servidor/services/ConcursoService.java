package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConcursoService extends BaseService<Concurso, Long> {

    private final ConcursoDocenteRepository concursoDocenteRepository;

    public ConcursoService(
            ConcursoRepository concursoRepository,
            ConcursoDocenteRepository concursoDocenteRepository) {

        super(concursoRepository);
        this.concursoDocenteRepository = concursoDocenteRepository;
    }

    public Docente obtenerPrimero(Long concursoId) {

        List<ConcursoDocente> ordenMerito =
                concursoDocenteRepository
                        .findByConcursoIdOrderByDocentePuntajeDesc(concursoId);

        if (ordenMerito.isEmpty()) {
            return null;
        }

        return ordenMerito.get(0).getDocente();
    }

    public Docente obtenerSiguienteEnOrden(
            Long concursoId,
            Long idDocenteActual) {

        List<ConcursoDocente> ordenMerito =
                concursoDocenteRepository
                        .findByConcursoIdOrderByDocentePuntajeDesc(concursoId);

        for (int i = 0; i < ordenMerito.size(); i++) {

            Docente docenteActual =
                    ordenMerito.get(i).getDocente();

            if (docenteActual.getId().equals(idDocenteActual)) {

                if (i + 1 < ordenMerito.size()) {
                    return ordenMerito
                            .get(i + 1)
                            .getDocente();
                }

                return null;
            }
        }

        return null;
    }
}