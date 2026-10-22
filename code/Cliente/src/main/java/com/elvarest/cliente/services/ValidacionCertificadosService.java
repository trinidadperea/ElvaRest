package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.TipoCertificadoDTO;
import com.elvarest.cliente.dto.enums.EstadoPostulacion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Paso 2 del flujo de concursos: aprobar o rechazar la certificación de una postulación.
 * Reglas que antes estaban en ConcursoController. El Servidor vuelve a validar todo
 * (incluido que esté cargado el certificado obligatorio) y su mensaje se muestra tal cual.
 */
@Service
public class ValidacionCertificadosService {

    private final ConcursoDocenteService concursoDocenteService;
    private final TipoCertificadoService tipoCertificadoService;
    private final FlujoConcursoService flujoService;

    public ValidacionCertificadosService(ConcursoDocenteService concursoDocenteService,
                                         TipoCertificadoService tipoCertificadoService,
                                         FlujoConcursoService flujoService) {
        this.concursoDocenteService = concursoDocenteService;
        this.tipoCertificadoService = tipoCertificadoService;
        this.flujoService = flujoService;
    }

    /** El paso 2 solo aplica con el concurso abierto y la postulación pendiente de certificados. */
    public void validarPendiente(ConcursoDTO concurso, ConcursoDocenteDTO postulacion) {
        if (flujoService.esCerrado(concurso)) {
            throw new IllegalArgumentException("El concurso está cerrado: no se pueden validar certificados.");
        }
        if (flujoService.estadoEfectivo(postulacion) != EstadoPostulacion.PENDIENTE_CERTIFICADOS) {
            throw new IllegalArgumentException("La postulación no está pendiente de certificados.");
        }
    }

    /**
     * Aplica la decisión (APROBAR / RECHAZAR) y devuelve el mensaje para el usuario.
     * Aprobar: pasa a pendiente de posesión. Rechazar: se cancela la reserva y se restituyen las horas.
     */
    public String resolver(ConcursoDTO concurso, ConcursoDocenteDTO postulacion, String decision, String observacion) {
        validarPendiente(concurso, postulacion);
        boolean aprobar = "APROBAR".equalsIgnoreCase(decision);
        boolean rechazar = "RECHAZAR".equalsIgnoreCase(decision);
        if (!aprobar && !rechazar) {
            throw new IllegalArgumentException("Seleccione Aprobar o Rechazar la certificación.");
        }
        if (observacion != null && !observacion.isBlank()) {
            postulacion.setObservacion(observacion.trim());
        }

        if (aprobar) {
            postulacion.setEstadoPostulacion(EstadoPostulacion.DESIGNADO_PENDIENTE_POSESION);
            concursoDocenteService.modificar(postulacion.getId(), postulacion);
            return "Certificación aprobada: el docente queda pendiente de la toma de posesión.";
        }

        postulacion.setEstadoPostulacion(EstadoPostulacion.CANCELADO_INEFECTIVO);
        if (postulacion.getObservacion() == null || postulacion.getObservacion().isBlank()) {
            postulacion.setObservacion("Certificados rechazados / no presentados.");
        }
        concursoDocenteService.modificar(postulacion.getId(), postulacion);
        Integer liberadas = flujoService.horasEfectivas(postulacion);
        return "Se canceló la reserva del docente"
                + (liberadas != null ? " y se restituyeron " + liberadas + " h al concurso." : ".");
    }

    /** Solo informativo para la pantalla: tipos obligatorios sin certificado actual en la postulación. */
    public List<TipoCertificadoDTO> obligatoriosFaltantes(List<CertificadoDTO> certificadosDePostulacion) {
        return tipoCertificadoService.listarActivos().stream()
                .filter(TipoCertificadoDTO::isObligatorio)
                .filter(tipo -> certificadosDePostulacion.stream()
                        .noneMatch(certificado -> certificado.isActual() && certificado.getTipoCertificado() != null
                                && Objects.equals(certificado.getTipoCertificado().getId(), tipo.getId())))
                .toList();
    }
}