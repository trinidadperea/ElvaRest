package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.enums.CaracterCertificado;
import com.elvarest.servidor.entities.enums.PrioridadAlerta;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.AlertaRepository;
import com.elvarest.servidor.repositories.CertificadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class AlertaService extends BaseService<Alerta, Long> {

    /** Faltando 7 días o menos: alerta de prioridad MEDIA. */
    public static final int DIAS_ALERTA_MEDIA = 7;
    /** Faltando 3 días o menos: alerta de prioridad ALTA. */
    public static final int DIAS_ALERTA_ALTA = 3;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AlertaRepository alertaRepository;
    private final CertificadoRepository certificadoRepository;

    public AlertaService(AlertaRepository repository, CertificadoRepository certificadoRepository) {
        super(repository);
        this.alertaRepository = repository;
        this.certificadoRepository = certificadoRepository;
    }

    /** El certificado de origen no se cambia desde la API (p. ej. al marcar la alerta como leída). */
    @Override
    protected void preModificacion(Alerta alerta) throws ErrorServiceException {
        alertaRepository.findById(alerta.getId())
                .ifPresent(guardada -> alerta.setCertificado(guardada.getCertificado()));
    }

    /** Revisa todos los certificados provisorios actuales. Lo invoca el scheduler diario. */
    @Transactional
    public int generarAlertasDeVencimiento() {
        int creadas = 0;
        for (Certificado certificado
                : certificadoRepository.findByCaracterAndActualTrueAndEliminadoFalse(CaracterCertificado.PROVISORIO)) {
            if (evaluarVencimiento(certificado)) {
                creadas++;
            }
        }
        return creadas;
    }

    /**
     * Crea la alerta que corresponda según los días que faltan para el vencimiento, sin duplicarla:
     * 3 días o menos -> ALTA (también si ya venció y nunca se avisó); 7 días o menos -> MEDIA.
     * No alerta certificados reemplazados, definitivos ni de postulaciones canceladas.
     *
     * @return true si se creó una alerta.
     */
    public boolean evaluarVencimiento(Certificado certificado) {
        if (certificado.getCaracter() != CaracterCertificado.PROVISORIO
                || !certificado.isActual()
                || Boolean.TRUE.equals(certificado.getEliminado())
                || certificado.getFechaVencimiento() == null) {
            return false;
        }
        ConcursoDocente postulacion = certificado.getConcursoDocente();
        if (postulacion != null && ReglasConcurso.esCancelada(postulacion)) {
            return false;
        }

        long dias = ChronoUnit.DAYS.between(LocalDate.now(), certificado.getFechaVencimiento());
        PrioridadAlerta prioridad = dias <= DIAS_ALERTA_ALTA ? PrioridadAlerta.ALTA
                : dias <= DIAS_ALERTA_MEDIA ? PrioridadAlerta.MEDIA
                : null;
        if (prioridad == null || alertaRepository.existsByCertificadoIdAndPrioridad(certificado.getId(), prioridad)) {
            return false;
        }

        Alerta alerta = new Alerta();
        alerta.setMensaje(mensaje(certificado, dias));
        alerta.setFechaEmision(LocalDate.now());
        alerta.setLeida(false);
        alerta.setPrioridad(prioridad);
        alerta.setCertificado(certificado);
        alerta.setEliminado(false);
        alertaRepository.save(alerta);
        return true;
    }

    /** Ej.: "El apto psicofísico provisorio de Pérez, Juan (Concurso Matemática 1° año) vence el 25/10/2026." */
    private String mensaje(Certificado certificado, long dias) {
        String tipo = certificado.getTipoCertificado() != null && certificado.getTipoCertificado().getNombre() != null
                ? certificado.getTipoCertificado().getNombre().toLowerCase(Locale.forLanguageTag("es"))
                : "certificado";
        ConcursoDocente postulacion = certificado.getConcursoDocente();
        String docente = postulacion != null && postulacion.getDocente() != null
                ? postulacion.getDocente().getApellido() + ", " + postulacion.getDocente().getNombre()
                : "docente sin datos";
        String concurso = postulacion != null && postulacion.getConcurso() != null
                ? (postulacion.getConcurso().getDescripcion() != null
                ? postulacion.getConcurso().getDescripcion()
                : "#" + postulacion.getConcurso().getId())
                : "sin datos";
        String verbo = dias < 0 ? "venció el " : "vence el ";
        return "El " + tipo + " provisorio de " + docente + " (Concurso " + concurso + ") "
                + verbo + certificado.getFechaVencimiento().format(FORMATO_FECHA) + ".";
    }
}