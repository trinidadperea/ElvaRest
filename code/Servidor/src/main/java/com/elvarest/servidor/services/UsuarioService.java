package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
        if (entidad.getNombreUsuario() == null || entidad.getNombreUsuario().isBlank()) {
            throw new ErrorServiceException("El usuario debe tener un nombre de usuario");
        }
        usuarioRepository.findByNombreUsuario(entidad.getNombreUsuario())
                .filter(existente -> !existente.getId().equals(entidad.getId()))
                .ifPresent(existente -> {
                    throw new ErrorServiceException("Ya existe un usuario con ese nombre de usuario");
                });
    }

    /** La contraseña inicial siempre coincide con el nombre de usuario y exige cambio en el primer inicio. */
    @Override
    protected void preAlta(Usuario entidad) throws ErrorServiceException {
        entidad.setContraseña(passwordEncoder.encode(entidad.getNombreUsuario()));
        entidad.setDebeCambiarPassword(true);
    }

    /** La modificación solo alcanza al nombre de usuario: rol, contraseña y datos personales se conservan. */
    @Override
    public Optional<Usuario> modificar(Long id, Usuario nuevo) throws ErrorServiceException {
        Optional<Usuario> actual = usuarioRepository.findById(id);
        if (actual.isEmpty()) {
            return Optional.empty();
        }
        actual.get().setNombreUsuario(nuevo.getNombreUsuario());
        return super.modificar(id, actual.get());
    }

    /** Un administrador no puede darse de baja a sí mismo: no quedaría quién gestione usuarios. */
    @Override
    protected void preBaja(Long id) throws ErrorServiceException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return;
        }
        usuarioRepository.findByNombreUsuario(auth.getName())
                .filter(usuario -> usuario.getId().equals(id))
                .ifPresent(usuario -> {
                    throw new ErrorServiceException("No se puede eliminar el usuario con la sesión activa");
                });
    }

    /** Restablece la contraseña: vuelve a ser el nombre de usuario y reactiva el cambio obligatorio. */
    public Usuario restablecerContraseña(Long id) throws ErrorServiceException {
        Optional<Usuario> encontrado = usuarioRepository.findById(id);
        if (encontrado.isEmpty() || Boolean.TRUE.equals(encontrado.get().getEliminado())) {
            throw new ErrorServiceException("Usuario no encontrado");
        }
        Usuario usuario = encontrado.get();
        usuario.setContraseña(passwordEncoder.encode(usuario.getNombreUsuario()));
        usuario.setDebeCambiarPassword(true);
        return usuarioRepository.save(usuario);
    }

    /** El usuario cambia su propia contraseña: verifica la actual y limpia el flag de cambio obligatorio. */
    public Usuario cambiarContraseña(String nombreUsuario, String contraseñaActual, String contraseñaNueva) throws ErrorServiceException {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ErrorServiceException("Usuario no encontrado"));
        if (contraseñaNueva == null || contraseñaNueva.isBlank()) {
            throw new ErrorServiceException("La nueva contraseña no puede estar vacía");
        }
        if (!passwordEncoder.matches(contraseñaActual, usuario.getContraseña())) {
            throw new ErrorServiceException("La contraseña actual no es correcta");
        }
        usuario.setContraseña(passwordEncoder.encode(contraseñaNueva));
        usuario.setDebeCambiarPassword(false);
        return usuarioRepository.save(usuario);
    }
}
