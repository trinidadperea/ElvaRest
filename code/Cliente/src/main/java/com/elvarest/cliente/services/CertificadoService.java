package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.CertificadosPostulacionDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CertificadoService extends BaseService<CertificadoDTO, Long> {

    private static final String API_URL = "http://localhost:8080/certificados";

    /** Más reciente primero (fecha de carga y, a igual fecha, id). */
    private static final Comparator<CertificadoDTO> MAS_RECIENTE_PRIMERO =
            Comparator.comparing(CertificadoDTO::getFechaCarga, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(CertificadoDTO::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ConcursoDocenteService concursoDocenteService;
    private final FlujoConcursoService flujoService;

    public CertificadoService(ConcursoDocenteService concursoDocenteService, FlujoConcursoService flujoService) {
        super(API_URL, CertificadoDTO.class);
        this.concursoDocenteService = concursoDocenteService;
        this.flujoService = flujoService;
    }

    /** Certificados (actuales e historial) de una postulación. */
    public List<CertificadoDTO> listarPorPostulacion(Long postulacionId) {
        return listarActivos().stream()
                .filter(certificado -> certificado.getConcursoDocente() != null
                        && Objects.equals(certificado.getConcursoDocente().getId(), postulacionId))
                .sorted(MAS_RECIENTE_PRIMERO)
                .toList();
    }

    /**
     * Certificados del docente agrupados por concurso, del concurso más reciente al más antiguo.
     * Incluye las postulaciones con certificados cargados y las no canceladas (para poder cargarlos).
     */
    public List<CertificadosPostulacionDTO> agruparPorDocente(Long docenteId) {
        Map<Long, List<CertificadoDTO>> porPostulacion = listarActivos().stream()
                .filter(certificado -> certificado.getConcursoDocente() != null
                        && certificado.getConcursoDocente().getId() != null
                        && certificado.getConcursoDocente().getDocente() != null
                        && Objects.equals(certificado.getConcursoDocente().getDocente().getId(), docenteId))
                .collect(Collectors.groupingBy(certificado -> certificado.getConcursoDocente().getId()));

        return concursoDocenteService.listarActivos().stream()
                .filter(postulacion -> postulacion.getDocente() != null
                        && Objects.equals(postulacion.getDocente().getId(), docenteId))
                .filter(postulacion -> porPostulacion.containsKey(postulacion.getId())
                        || !flujoService.estadoEfectivo(postulacion).isCancelado())
                .sorted(Comparator.comparing(ConcursoDocenteDTO::getFechaEmision,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(postulacion -> agrupar(postulacion, porPostulacion.getOrDefault(postulacion.getId(), List.of())))
                .toList();
    }

    private CertificadosPostulacionDTO agrupar(ConcursoDocenteDTO postulacion, List<CertificadoDTO> certificados) {
        List<CertificadoDTO> ordenados = certificados.stream().sorted(MAS_RECIENTE_PRIMERO).toList();
        return new CertificadosPostulacionDTO(
                postulacion,
                ordenados.stream().filter(CertificadoDTO::isActual).toList(),
                ordenados.stream().filter(certificado -> !certificado.isActual()).toList(),
                !flujoService.estadoEfectivo(postulacion).isCancelado());
    }
}