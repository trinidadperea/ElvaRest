package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.repositories.UsuarioRepository;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService extends BaseService<Usuario, Long> {

    public UsuarioService(UsuarioRepository repository) {
        super(repository);
    }
}
