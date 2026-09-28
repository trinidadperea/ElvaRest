package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.TipoCertificado;

import java.util.Optional;

public interface TipoCertificadoRepository extends BaseRepository<TipoCertificado, Long> {
    Optional<TipoCertificado> findByCodigo(String codigo);
}
