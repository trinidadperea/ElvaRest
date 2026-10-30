package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class TipoCertificadoService extends BaseService<TipoCertificado, Long> {
    private final TipoCertificadoRepository tipoCertificadoRepository;
    private final CertificadoRepository certificadoRepository;

    public TipoCertificadoService(TipoCertificadoRepository repository,
                                  CertificadoRepository certificadoRepository) {
        super(repository);
        this.tipoCertificadoRepository = repository;
        this.certificadoRepository = certificadoRepository;
    }
    @Override
    protected void validar(TipoCertificado entidad) throws ErrorServiceException {
        tipoCertificadoRepository.findByCodigo(entidad.getCodigo())
                .filter(existente -> !existente.getId().equals(entidad.getId()))
                .ifPresent(existente -> {
                    throw new RuntimeException("Ya existe un tipo de certificado con ese código");
                });
    }

    /**
     * Regla de eliminación: un tipo de certificado con certificados cargados en concursos no se
     * elimina. Los certificados lo referencian de forma obligatoria y la postulación ya se validó
     * con ese tipo: borrarlo dejaría esos certificados sin tipo y sin control de obligatoriedad.
     */
    public void validarEliminacion(Long id) throws ErrorServiceException {
        Optional<TipoCertificado> encontrado = repository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            return;
        }
        boolean cargadoEnConcursos = certificadoRepository.findAll().stream()
                .filter(certificado -> !Boolean.TRUE.equals(certificado.getEliminado()))
                .filter(certificado -> certificado.getTipoCertificado() != null
                        && Objects.equals(certificado.getTipoCertificado().getId(), id))
                .findAny().isPresent();
        if (cargadoEnConcursos) {
            throw new ErrorServiceException("No se puede eliminar el tipo de certificado porque hay "
                    + "certificados cargados de este tipo en concursos.");
        }
    }

    /** La regla se aplica en el servicio: cualquier vía de baja queda validada, no solo el DELETE. */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        validarEliminacion(id);
    }
}
