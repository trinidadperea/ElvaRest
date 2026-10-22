package com.elvarest.cliente.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Vista: certificados de un docente agrupados por postulación (concurso). No viaja al Servidor. */
@Getter
@AllArgsConstructor
public class CertificadosPostulacionDTO {
    private ConcursoDocenteDTO postulacion;
    /** Un certificado actual por tipo. */
    private List<CertificadoDTO> actuales;
    /** Certificados reemplazados, del más reciente al más antiguo */
    private List<CertificadoDTO> historial;
    /** Se pueden cargar/renovar certificados  */
    private boolean admiteCarga;
}
