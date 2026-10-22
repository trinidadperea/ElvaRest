package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.enums.CaracterCertificado;

import java.util.List;

public interface CertificadoRepository extends BaseRepository<Certificado, Long> {

    /** Certificado(s) actual(es) de un tipo en una postulación (normalmente uno solo). */
    List<Certificado> findByConcursoDocenteIdAndTipoCertificadoIdAndActualTrueAndEliminadoFalse(
            Long concursoDocenteId, Long tipoCertificadoId);

    /** Certificados actuales de una postulación (uno por tipo). */
    List<Certificado> findByConcursoDocenteIdAndActualTrueAndEliminadoFalse(Long concursoDocenteId);

    /** Certificados actuales de un carácter dado (el scheduler usa PROVISORIO). */
    List<Certificado> findByCaracterAndActualTrueAndEliminadoFalse(CaracterCertificado caracter);
}
