package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService extends BaseService<Usuario, Long> {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        super(repository);
        this.usuarioRepository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void validar(Usuario entidad) throws ErrorServiceException {
        if (entidad.getRol() == null) {
            throw new ErrorServiceException("El usuario debe tener un rol asignado");
        }
        usuarioRepository.findByNombreUsuario(entidad.getNombreUsuario())
                .filter(existente -> !existente.getId().equals(entidad.getId()))
                .ifPresent(existente -> {
                    throw new ErrorServiceException("Ya existe un usuario con ese nombre de usuario");
                });
    }

    @Override
    protected void preAlta(Usuario entidad) throws ErrorServiceException {
        entidad.setContraseña(passwordEncoder.encode(entidad.getContraseña()));
    }
}
