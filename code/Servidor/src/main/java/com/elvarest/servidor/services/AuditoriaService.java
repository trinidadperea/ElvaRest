package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Auditoria;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.AuditoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditoriaService extends BaseService<Auditoria, Long> {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository repository) {
        super(repository);
        this.auditoriaRepository = repository;
    }

    /** El historial se muestra de lo más reciente a lo más antiguo. */
    @Override
    public List<Auditoria> listarActivos() throws ErrorServiceException {
        try {
            return auditoriaRepository.findAllByOrderByFechaHoraDesc();
        } catch (Exception e) {
            throw new ErrorServiceException("Error de Sistemas");
        }
    }

    /**
     * Registra un evento desde el aspecto de auditoría. Usa repository.save (no alta) para
     * quedar fuera de los métodos interceptados y no auto-auditarse. El uuid público lo
     * asigna el servidor, igual que en las altas.
     */
    public Auditoria registrar(Auditoria auditoria) {
        auditoria.setEliminado(false);
        auditoria.setUuid(UUID.randomUUID().toString());
        return auditoriaRepository.save(auditoria);
    }
}
