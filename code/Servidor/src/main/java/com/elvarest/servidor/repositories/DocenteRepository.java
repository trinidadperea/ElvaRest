package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.Docente;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocenteRepository extends BaseRepository<Docente, Long> {
    @Query("SELECT d FROM Docente d WHERE d.activo = true ORDER BY d.nombre ASC, d.apellido ASC")
    List<Docente> findActiveDocentesOrdenados();



}
