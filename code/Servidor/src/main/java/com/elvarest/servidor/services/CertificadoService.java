package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.entities.enums.CaracterCertificado;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Certificados de una postulación (docente + concurso).
 * - Se cargan siempre de cero en cada concurso (no se reutilizan los de otro concurso).
 * - Solo los tipos que lo permiten (apto psicofísico) pueden ser provisorios, con vencimiento posterior a hoy.
 * - No se editan: renovar = cargar uno nuevo; el anterior del mismo tipo queda en el historial (actual = false).
 * - Al cargar un provisorio se evalúa en el momento si corresponde alerta de vencimiento; al renovar,
 *   la alerta del certificado reemplazado se resuelve en el momento (no espera la depuración diaria).
 * - No se eliminan: forman parte de la postulación y se reemplazan cargando uno nuevo del mismo tipo.
 */
@Service
public class CertificadoService extends BaseService<Certificado, Long> {

    private final CertificadoRepository certificadoRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;
    private final TipoCertificadoRepository tipoCertificadoRepository;
    private final AlertaService alertaService;

    public CertificadoService(CertificadoRepository repository,
                              ConcursoDocenteRepository concursoDocenteRepository,
                              TipoCertificadoRepository tipoCertificadoRepository,
                              AlertaService alertaService) {
        super(repository);
        this.certificadoRepository = repository;
        this.concursoDocenteRepository = concursoDocenteRepository;
        this.tipoCertificadoRepository = tipoCertificadoRepository;
        this.alertaService = alertaService;
    }

    /** Alta, reemplazo del anterior y alerta inmediata van juntos: si algo falla no queda nada a medias. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Certificado alta(Certificado entidad) throws ErrorServiceException {
        return super.alta(entidad);
    }

    @Override
    protected void validar(Certificado certificado) throws ErrorServiceException {
        ConcursoDocente postulacion = postulacionDe(certificado);
        if (ReglasConcurso.esCancelada(postulacion)) {
            throw new ErrorServiceException("La postulación está cancelada: no se pueden cargar certificados.");
        }
        TipoCertificado tipo = tipoDe(certificado);
        certificado.setConcursoDocente(postulacion);
        certificado.setTipoCertificado(tipo);

        if (!tipo.isPermiteProvisorio()) {
            if (certificado.getCaracter() == CaracterCertificado.PROVISORIO) {
                throw new ErrorServiceException("El certificado «" + tipo.getNombre() + "» no puede ser provisorio.");
            }
            certificado.setCaracter(CaracterCertificado.DEFINITIVO);
        }
        if (certificado.getCaracter() == null) {
            throw new ErrorServiceException("Indique si el certificado es definitivo o provisorio.");
        }

        LocalDate vencimiento = certificado.getFechaVencimiento();
        if (certificado.getCaracter() == CaracterCertificado.PROVISORIO) {
            if (vencimiento == null) {
                throw new ErrorServiceException("Indique la fecha de vencimiento del certificado provisorio.");
            }
            if (!vencimiento.isAfter(LocalDate.now())) {
                throw new ErrorServiceException("La fecha de vencimiento debe ser posterior a hoy.");
            }
        } else if (vencimiento != null) {
            throw new ErrorServiceException("Un certificado definitivo no debe tener fecha de vencimiento.");
        }
    }

    @Override
    protected void preAlta(Certificado certificado) throws ErrorServiceException {
        // Valores que no dependen del cliente.
        certificado.setFechaCarga(LocalDate.now());
        certificado.setActual(true);
    }

    @Override
    protected void postAlta(Certificado guardado) throws ErrorServiceException {
        // El certificado anterior del mismo tipo pasa al historial.
        certificadoRepository.findByConcursoDocenteIdAndTipoCertificadoIdAndActualTrueAndEliminadoFalse(
                        guardado.getConcursoDocente().getId(), guardado.getTipoCertificado().getId()).stream()
                .filter(anterior -> !Objects.equals(anterior.getId(), guardado.getId()))
                .forEach(anterior -> {
                    anterior.setActual(false);
                    certificadoRepository.save(anterior);
                    // La causa de su alerta desapareció: queda leída en el acto, no en la revisión diaria.
                    alertaService.evaluarVencimiento(anterior);
                });

        // Si al provisorio ya le quedan 7 días o menos, la alerta se genera en el momento.
        alertaService.evaluarVencimiento(guardado);
    }

    /** Al dar de baja el certificado, su alerta de vencimiento ya no tiene causa: queda leída en el acto. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bajaLogica(Long id) throws ErrorServiceException {
        boolean dadoDeBaja = super.bajaLogica(id);
        if (dadoDeBaja) {
            repository.findById(id).ifPresent(alertaService::evaluarVencimiento);
        }
        return dadoDeBaja;
    }

    @Override
    protected void preModificacion(Certificado certificado) throws ErrorServiceException {
        throw new ErrorServiceException("Los certificados no se modifican: para renovarlo, cargue uno nuevo "
                + "(el anterior queda en el historial).");
    }

    /**
     * Los certificados no se eliminan: se reemplazan cargando uno nuevo del mismo tipo y el anterior
     * pasa al historial. Borrarlos dejaría la postulación sin el respaldo que la habilitó.
     */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        Optional<Certificado> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return; // BaseService responde "no encontrada".
        }
        throw new ErrorServiceException("Los certificados no se eliminan: para reemplazarlo, cargue uno nuevo "
                + "(el anterior queda en el historial).");
    }

    private ConcursoDocente postulacionDe(Certificado certificado) throws ErrorServiceException {
        Long id = certificado.getConcursoDocente() == null ? null : certificado.getConcursoDocente().getId();
        if (id == null) {
            throw new ErrorServiceException("El certificado debe estar asociado a una postulación (docente y concurso).");
        }
        return concursoDocenteRepository.findById(id)
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("No se encontró la postulación."));
    }

    private TipoCertificado tipoDe(Certificado certificado) throws ErrorServiceException {
        Long id = certificado.getTipoCertificado() == null ? null : certificado.getTipoCertificado().getId();
        if (id == null) {
            throw new ErrorServiceException("Seleccione el tipo de certificado.");
        }
        return tipoCertificadoRepository.findById(id)
                .filter(tipo -> !Boolean.TRUE.equals(tipo.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("No se encontró el tipo de certificado."));
    }
}