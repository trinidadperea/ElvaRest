package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.entities.enums.Rol;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reglas de gestión de usuarios: la contraseña inicial es el nombre de usuario con cambio obligatorio,
 * la modificación solo alcanza al nombre de usuario, un administrador no puede darse de baja a sí mismo
 * y el restablecimiento/cambio de contraseña mantienen actualizado el flag de cambio obligatorio.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, passwordEncoder);
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(Long id, String nombreUsuario) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombreUsuario(nombreUsuario);
        usuario.setRol(Rol.USER);
        usuario.setContraseña("hashExistente");
        usuario.setDebeCambiarPassword(false);
        return usuario;
    }

    private void sesionActiva(String nombreUsuario) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(nombreUsuario, "clave", List.of()));
        SecurityContextHolder.setContext(contexto);
    }

    @Test
    void altaAsignaContraseñaIgualAlNombreDeUsuarioYActivaElFlag() {
        Usuario nuevo = usuario(null, "jperez");
        nuevo.setContraseña("otra");
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("jperez")).thenReturn("hashJperez");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario guardado = service.alta(nuevo);

        assertEquals("hashJperez", guardado.getContraseña());
        assertTrue(Boolean.TRUE.equals(guardado.getDebeCambiarPassword()));
        assertFalse(Boolean.TRUE.equals(guardado.getEliminado()));
    }

    @Test
    void altaRechazaNombreDeUsuarioDuplicado() {
        Usuario nuevo = usuario(null, "jperez");
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuario(5L, "jperez")));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(nuevo));
        assertEquals("Ya existe un usuario con ese nombre de usuario", ex.getMessage());
    }

    @Test
    void altaRechazaNombreDeUsuarioVacio() {
        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(usuario(null, "")));
        assertEquals("El usuario debe tener un nombre de usuario", ex.getMessage());
    }

    @Test
    void modificarSoloActualizaElNombreDeUsuario() {
        Usuario actual = usuario(1L, "jperez");
        actual.setRol(Rol.ADMIN);
        actual.setDebeCambiarPassword(true);

        Usuario enviado = usuario(1L, "jperez2");
        enviado.setRol(Rol.USER);
        enviado.setContraseña("intentoDeCambio");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(actual));
        when(usuarioRepository.findByNombreUsuario("jperez2")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Usuario> resultado = service.modificar(1L, enviado);

        assertTrue(resultado.isPresent());
        Usuario modificado = resultado.get();
        assertEquals("jperez2", modificado.getNombreUsuario());
        assertEquals(Rol.ADMIN, modificado.getRol());
        assertEquals("hashExistente", modificado.getContraseña());
        assertTrue(Boolean.TRUE.equals(modificado.getDebeCambiarPassword()));
    }

    @Test
    void modificarRechazaNombreDeUsuarioDuplicado() {
        Usuario actual = usuario(1L, "jperez");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(actual));
        when(usuarioRepository.findByNombreUsuario("jperez2")).thenReturn(Optional.of(usuario(9L, "jperez2")));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class,
                () -> service.modificar(1L, usuario(1L, "jperez2")));
        assertEquals("Ya existe un usuario con ese nombre de usuario", ex.getMessage());
    }

    @Test
    void modificarUsuarioInexistenteDevuelveVacio() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<Usuario> resultado = service.modificar(1L, usuario(1L, "jperez"));

        assertTrue(resultado.isEmpty());
    }

    @Test
    void restablecerContraseñaVuelveAlNombreDeUsuarioYReactivaElFlag() {
        Usuario usuario = usuario(1L, "jperez");
        usuario.setContraseña("hashViejo");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("jperez")).thenReturn("hashJperez");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario restablecido = service.restablecerContraseña(1L);

        assertEquals("hashJperez", restablecido.getContraseña());
        assertTrue(Boolean.TRUE.equals(restablecido.getDebeCambiarPassword()));
    }

    @Test
    void restablecerContraseñaDeUsuarioInexistenteLanzaError() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.restablecerContraseña(1L));
        assertEquals("Usuario no encontrado", ex.getMessage());
    }

    @Test
    void restablecerContraseñaDeUsuarioEliminadoLanzaError() {
        Usuario eliminado = usuario(1L, "jperez");
        eliminado.setEliminado(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(eliminado));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.restablecerContraseña(1L));
        assertEquals("Usuario no encontrado", ex.getMessage());
    }

    @Test
    void cambiarContraseñaPropiaVerificaLaActualYLimpiaElFlag() {
        Usuario usuario = usuario(1L, "jperez");
        usuario.setDebeCambiarPassword(true);
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("actual123", "hashExistente")).thenReturn(true);
        when(passwordEncoder.encode("nueva456")).thenReturn("hashNueva");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario cambiado = service.cambiarContraseña("jperez", "actual123", "nueva456");

        assertEquals("hashNueva", cambiado.getContraseña());
        assertFalse(Boolean.TRUE.equals(cambiado.getDebeCambiarPassword()));
    }

    @Test
    void cambiarContraseñaConContraseñaActualIncorrectaLanzaError() {
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuario(1L, "jperez")));
        when(passwordEncoder.matches("incorrecta", "hashExistente")).thenReturn(false);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class,
                () -> service.cambiarContraseña("jperez", "incorrecta", "nueva456"));
        assertEquals("La contraseña actual no es correcta", ex.getMessage());
    }

    @Test
    void cambiarContraseñaConNuevaVaciaLanzaError() {
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuario(1L, "jperez")));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class,
                () -> service.cambiarContraseña("jperez", "actual123", ""));
        assertEquals("La nueva contraseña no puede estar vacía", ex.getMessage());
    }

    @Test
    void bajaDelUsuarioConSesionActivaRechazada() {
        Usuario propio = usuario(1L, "jperez");
        propio.setRol(Rol.ADMIN);
        sesionActiva("jperez");
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(propio));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.bajaLogica(1L));
        assertEquals("No se puede eliminar el usuario con la sesión activa", ex.getMessage());
    }

    @Test
    void bajaDeOtroUsuarioPermitida() {
        sesionActiva("admin");
        when(usuarioRepository.findByNombreUsuario("admin")).thenReturn(Optional.of(usuario(2L, "admin")));

        Usuario objetivo = usuario(1L, "jperez");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(objetivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean eliminado = service.bajaLogica(1L);

        assertTrue(eliminado);
        assertTrue(Boolean.TRUE.equals(objetivo.getEliminado()));
    }
}
