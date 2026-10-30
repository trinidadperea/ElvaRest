package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.BaseEntity;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.BaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public abstract class BaseService<T extends BaseEntity<ID>, ID> {

    private static final Logger log = LoggerFactory.getLogger(BaseService.class);

    protected final BaseRepository<T, ID> repository;

    protected BaseService(BaseRepository<T, ID> repository) {
        this.repository = repository;
    }

    public T alta(T entidad) throws ErrorServiceException {
        try {

            validar(entidad);
            preAlta(entidad);

            entidad.setEliminado(false);
            T guardado = repository.save(entidad);

            postAlta(guardado);
            return guardado;

        }catch(Exception e) {
            // La traza queda en el log del Servidor: al Cliente solo se le devuelve el mensaje de la causa.
            log.error("Error al dar de alta {}", entidad.getClass().getSimpleName(), e);
            throw new ErrorServiceException(e.getMessage());
        }
    }

    public Optional<T> modificar(ID id, T entidadNueva)throws ErrorServiceException {
        try {

            // El id se asigna antes de validar para que las subclases puedan comparar con el registro guardado.
            entidadNueva.setId(id);
            validar(entidadNueva);
            preModificacion(entidadNueva);
            return repository.findById(id).map(entidad -> {
                T actualizado = repository.save(entidadNueva);
                return actualizado;
            });

        }catch(Exception e) {
            throw new ErrorServiceException(e.getMessage());
        }
    }

    public boolean bajaLogica(ID id)throws ErrorServiceException {
        try {

            preBaja(id);

            return repository.findById(id).map(entidad -> {
                entidad.setEliminado(true);
                repository.save(entidad);
                return true;
            }).orElse(false);

        }catch(ErrorServiceException e) {
            // Las reglas de baja (preBaja) conservan su mensaje: el usuario ve la causa y no un error genérico.
            throw e;
        }catch(Exception e) {
            throw new ErrorServiceException("Error de Sistemas");
        }
    }

    public Optional<T> obtener(ID id)throws ErrorServiceException {
        try {

            return repository.findById(id).
                    filter(e -> !Boolean.TRUE.equals(e.getEliminado()));

        }catch(Exception e) {
            throw new ErrorServiceException("Error de Sistemas");
        }
    }

    public List<T> listarActivos()throws ErrorServiceException {
        try {

            return repository.findAll().stream()
                    .filter(e -> !Boolean.TRUE.equals(e.getEliminado()))
                    .toList();

        }catch(Exception e) {
            throw new ErrorServiceException("Error de Sistemas");
        }
    }

    public T getOne(ID id) throws ErrorServiceException {
        try {
            Optional<T> entidad = repository.findById(id);
            if (entidad.isPresent() && !Boolean.TRUE.equals(entidad.get().getEliminado())) {
                return entidad.get();
            } else {
                throw new ErrorServiceException("Entidad no encontrada o eliminada");
            }
        } catch (Exception e) {
            throw new ErrorServiceException("Error de Sistemas");
        }
    }

    //Metodos para ser redefinidos en las clases de servicio que heredan, con el objetivo
    //que sea necesario realizar acciones previas o posteriores en las Altas, Bajas y
    //Modificaciones.
    //Se deberá redefinir el comportamiento en la clase que hereda.
    protected void validar(T entidad) throws ErrorServiceException {}
    protected void preAlta(T entidad) throws ErrorServiceException {}
    protected void postAlta(T entidad)throws ErrorServiceException {}
    protected void preModificacion(T entidad)throws ErrorServiceException {}
    protected void preBaja(ID id)throws ErrorServiceException {}
}
