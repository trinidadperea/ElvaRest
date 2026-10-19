package com.elvarest.servidor.config;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder encoder;
    private final String bootstrapUser;
    private final String bootstrapPassword;

    public DbUserDetailsService(UsuarioRepository usuarioRepository,
                                PasswordEncoder encoder,
                                @Value("${spring.security.user.name}") String bootstrapUser,
                                @Value("${spring.security.user.password}") String bootstrapPassword) {
        this.usuarioRepository = usuarioRepository;
        this.encoder = encoder;
        this.bootstrapUser = bootstrapUser;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Cuenta de arranque (variables de entorno): permite crear el primer ADMIN
        if (username.equals(bootstrapUser)) {
            return User.withUsername(bootstrapUser)
                    .password(encoder.encode(bootstrapPassword))
                    .roles("ADMIN")
                    .build();
        }
        Usuario u = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(u.getNombreUsuario())
                .password(u.getContraseña())
                .roles(u.getRol().name())
                .disabled(Boolean.TRUE.equals(u.getEliminado()))
                .build();
    }
}