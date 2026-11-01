package com.elvarest.cliente.controllers;

import com.elvarest.cliente.auth.LoginSuccessHandler;
import com.elvarest.cliente.dto.UsuarioDTO;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import com.elvarest.cliente.services.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gestión de usuarios del ADMIN. La API ya expone el UUID real de cada usuario como
 * identificador público: las URLs usan ese uuid directamente, sin mapas de sesión.
 * También resuelve el cambio de contraseña propia.
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioController extends BaseController<UsuarioDTO> {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService service) {
        super(service);
        this.usuarioService = service;
        initController(new UsuarioDTO(), "Lista de usuarios", "Editar usuario", "usuarios/");
    }

    // --- Detalle / edición / baja con UUID ------------------------------------

    @GetMapping("/ver/{uuid}")
    public String verUsuario(@PathVariable String uuid, Model model, RedirectAttributes attributes) {
        try {
            UsuarioDTO encontrado = usuarioService.obtener(uuid);
            if (encontrado == null) {
                throw new IllegalArgumentException("No encontrado: " + uuid);
            }
            model.addAttribute("item", encontrado);
            model.addAttribute("isDisabled", true);
            model.addAttribute("titleEdit", titleEdit);
            model.addAttribute("nameEntityLower", nameEntityLower);
            model.addAttribute("usuarioUuid", uuid);
            return viewDetail;
        } catch (ErrorServiceException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        } catch (Exception e) {
            attributes.addFlashAttribute("msgError", "Error de Sistema");
        }
        return redirectList;
    }

    @GetMapping("/editar/{uuid}")
    public String editarUsuario(@PathVariable String uuid, Model model, RedirectAttributes attributes) {
        try {
            UsuarioDTO encontrado = usuarioService.obtener(uuid);
            if (encontrado == null) {
                throw new IllegalArgumentException("No encontrado: " + uuid);
            }
            model.addAttribute("item", encontrado);
            model.addAttribute("isDisabled", false);
            model.addAttribute("titleEdit", titleEdit);
            model.addAttribute("nameEntityLower", nameEntityLower);
            model.addAttribute("usuarioUuid", uuid);
            return viewEdit;
        } catch (ErrorServiceException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        } catch (Exception e) {
            attributes.addFlashAttribute("msgError", "Error de Sistema");
        }
        return redirectList;
    }

    @PostMapping("/baja/{uuid}")
    public String darDeBaja(@PathVariable String uuid, RedirectAttributes attributes) {
        try {
            usuarioService.bajaLogica(uuid);
            attributes.addFlashAttribute("msgExito", "El usuario fue dado de baja correctamente.");
        } catch (ErrorServiceException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        } catch (Exception e) {
            attributes.addFlashAttribute("msgError", "Error de Sistema");
        }
        return redirectList;
    }

    /** Restablece la contraseña: vuelve a ser el nombreUsuario y se exige cambiarla (acción de ADMIN). */
    @PostMapping("/{uuid}/restablecer-contrasena")
    public String restablecerContrasena(@PathVariable String uuid, RedirectAttributes attributes) {
        try {
            usuarioService.restablecerContraseña(uuid);
            attributes.addFlashAttribute("msgExito",
                    "La contraseña fue restablecida: volvió a ser el nombre de usuario y se exigirá cambiarla en el próximo inicio.");
        } catch (ErrorServiceException e) {
            attributes.addFlashAttribute("msgError", e.getMessage());
        } catch (Exception e) {
            attributes.addFlashAttribute("msgError", "Error de Sistema");
        }
        return "redirect:/usuarios/ver/" + uuid;
    }

    // --- Cambio de contraseña propia (flag de primer ingreso) -----------------

    /** Pantalla obligatoria de cambio de contraseña: sin menú, como el login. */
    @GetMapping("/cambiar-contrasena")
    public String pantallaCambiarContrasena() {
        return "registration/cambiar-contrasena";
    }

    @PostMapping("/cambiar-contrasena")
    public String cambiarContrasena(@RequestParam String contraseñaActual,
                                    @RequestParam String contraseñaNueva,
                                    @RequestParam String contraseñaConfirmacion,
                                    HttpSession session, Model model, RedirectAttributes attributes) {
        try {
            if (!contraseñaNueva.equals(contraseñaConfirmacion)) {
                model.addAttribute("msgError", "La nueva contraseña y su confirmación no coinciden.");
                return "registration/cambiar-contrasena";
            }
            String username = (String) session.getAttribute(LoginSuccessHandler.ATRIBUTO_USUARIO_API);
            String password = (String) session.getAttribute(LoginSuccessHandler.ATRIBUTO_PASSWORD_API);
            usuarioService.cambiarContraseña(username, password, contraseñaActual, contraseñaNueva);
            session.removeAttribute(LoginSuccessHandler.ATRIBUTO_FLAG);
            attributes.addFlashAttribute("msgExito", "La contraseña fue actualizada correctamente.");
            return "redirect:/";
        } catch (ErrorServiceException e) {
            model.addAttribute("msgError", e.getMessage());
            return "registration/cambiar-contrasena";
        } catch (Exception e) {
            model.addAttribute("msgError", "Error de Sistema");
            return "registration/cambiar-contrasena";
        }
    }

    // --- Bloqueo de las rutas numéricas heredadas del CRUD genérico -----------

    /**
     * Las rutas con id numérico quedan deshabilitadas para usuarios: el navegador solo
     * debe moverse por los UUID públicos generados en el listado.
     */
    @Override
    public String consultar(String uuid, Model model, RedirectAttributes attributes) {
        attributes.addFlashAttribute("msgError", "Enlace no válido o expirado.");
        return redirectList;
    }

    @Override
    public String editar(String uuid, Model model, RedirectAttributes attributes) {
        attributes.addFlashAttribute("msgError", "Enlace no válido o expirado.");
        return redirectList;
    }

    @Override
    public String eliminar(String uuid, RedirectAttributes attributes) {
        attributes.addFlashAttribute("msgError", "Enlace no válido o expirado.");
        return redirectList;
    }
}
