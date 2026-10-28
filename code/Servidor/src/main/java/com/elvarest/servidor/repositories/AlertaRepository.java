package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.Alerta;

import java.util.List;

public interface AlertaRepository extends BaseRepository<Alerta, Long> {

    /** Alerta vigente de un certificado (incluye las leídas y las dadas de baja: hay una por causa). */
    List<Alerta> findByCertificadoId(Long certificadoId);

    /** Alertas generadas por un concurso (incluye las dadas de baja: la baja manual silencia la alerta). */
    List<Alerta> findByConcursoId(Long concursoId);
}
