package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.Auditoria;

import java.util.List;

public interface AuditoriaRepository extends BaseRepository<Auditoria, Long> {

    /** Historial de lo más reciente a lo más antiguo. */
    List<Auditoria> findAllByOrderByFechaHoraDesc();
}
