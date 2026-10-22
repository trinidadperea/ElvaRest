package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.enums.PrioridadAlerta;

public interface AlertaRepository extends BaseRepository<Alerta, Long> {

    /** Evita duplicar la alerta de un certificado para la misma prioridad (incluye las dadas de baja). */
    boolean existsByCertificadoIdAndPrioridad(Long certificadoId, PrioridadAlerta prioridad);
}
