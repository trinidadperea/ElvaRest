package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController extends BaseController<Usuario, Long> {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService service) {
        super(service);
        this.usuarioService = service;
    }

    /** El ADMIN restablece la contraseña de un usuario: vuelve a ser su nombre de usuario y exige cambio. */
    @PostMapping("/{id}/restablecer-contrasena")
    public ResponseEntity<?> restablecerContrasena(@PathVariable Long id) {
        try {
            Usuario actualizado = usuarioService.restablecerContraseña(id);
            return ResponseEntity.status(HttpStatus.OK).body(actualizado);
        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error al restablecer la contraseña"));
        }
    }

    /** El usuario cambia su propia contraseña: se verifica la actual y se limpia el flag de cambio obligatorio. */
    @PostMapping("/cambiar-contrasena")
    public ResponseEntity<?> cambiarContrasena(@RequestBody Map<String, String> datos, Principal principal) {
        try {
            Usuario actualizado = usuarioService.cambiarContraseña(
                    principal.getName(),
                    datos.get("contraseñaActual"),
                    datos.get("contraseñaNueva"));
            return ResponseEntity.status(HttpStatus.OK).body(actualizado);
        } catch (ErrorServiceException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error al cambiar la contraseña"));
        }
    }
}
